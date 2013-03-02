/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.modules.LocalLoader;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.Resource;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.SystemPaths;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XWiring;
import org.jboss.osgi.resolver.spi.AbstractBundleWire;
import org.jboss.osgi.resolver.spi.RemoveOnlyCollection;
import org.jboss.osgi.resolver.spi.ResolverHookProcessor;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

/**
 * A fallback loader that takes care of dynamic class/resource loads.
 *
 * @author thomas.diesler@jboss.com
 * @since 24-Feb-2012
 */
final class FallbackLoader implements LocalLoader {

    private static ThreadLocal<Map<String, AtomicInteger>> dynamicLoadAttempts;

    private final ReentrantLock fallbackLock = new ReentrantLock();
    private final AtomicBoolean fallbackEnabled = new AtomicBoolean(true);
    private final HostBundleState hostBundle;
    private final HostBundleRevision hostRev;
    private final Set<String> importedPaths;
    private final FrameworkState frameworkState;
    private final BundleManager bundleManager;

    private List<XPackageRequirement> weavingImports;

    FallbackLoader(HostBundleRevision hostRev, Set<String> importedPaths) {
        assert hostRev != null : "Null hostRev";
        assert importedPaths != null : "Null importedPaths";
        this.importedPaths = importedPaths;
        this.hostRev = hostRev;
        this.hostBundle = hostRev.getBundleState();
        this.bundleManager = hostBundle.getBundleManager();
        this.frameworkState = hostBundle.getFrameworkState();
        hostRev.setFallbackLoader(this);
    }

    boolean setEnabled(boolean flag) {
        lockFallbackLoader();
        try {
            return fallbackEnabled.getAndSet(flag);
        } finally {
            unlockFallbackLoader();
        }
    }

    void lockFallbackLoader() {
        fallbackLock.lock();
    }

    void unlockFallbackLoader() {
        fallbackLock.unlock();
    }

    void addDynamicWeavingImport(XPackageRequirement req) {
        if (weavingImports == null) {
            weavingImports = new ArrayList<XPackageRequirement>();
        }
        weavingImports.add(req);
    }

    @Override
    public Class<?> loadClassLocal(String className, boolean resolve) {
        DynamicLoadContext context = new DynamicLoadContext(className.replace('.', '/') + ".class");
        try {
            lockFallbackLoader();

            List<XPackageRequirement> matchingPatterns = findMatchingPatterns(className);
            if (!fallbackEnabled.get() || matchingPatterns.isEmpty())
                return null;

            findRevisionDynamically(context, matchingPatterns);
        } finally {
            unlockFallbackLoader();
        }

        Class<?> result = null;
        XBundleRevision brev = context.targetRevision;
        if (brev != null) {
            try {
                ModuleClassLoader moduleClassLoader = brev.getModuleClassLoader();
                result = moduleClassLoader.loadClass(className);
            } catch (ClassNotFoundException ex) {
                LOGGER.tracef("Cannot load class [%s] from module: %s", className, brev);
                return null;
            }
            if (context.capability != null && context.requirement != null) {
                BundleCapability bcap = (BundleCapability)context.capability;
                BundleRequirement breq = (BundleRequirement)context.requirement;
                AbstractBundleWire wire = new AbstractBundleWire(bcap, breq, brev, hostRev);
                XWiring requirerWiring = (XWiring) hostBundle.adapt(BundleWiring.class);
                XWiring providerWiring = (XWiring) brev.getBundle().adapt(BundleWiring.class);
                requirerWiring.addRequiredWire(wire);
                providerWiring.addRequiredWire(wire);
            }
        }
        return result;
    }

    @Override
    public Package loadPackageLocal(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Resource> loadResourceLocal(String resName) {
        DynamicLoadContext context = new DynamicLoadContext(resName);
        try {
            lockFallbackLoader();

            if (resName.startsWith("/"))
                resName = resName.substring(1);

            List<XPackageRequirement> matchingPatterns = findMatchingPatterns(resName);
            if (!fallbackEnabled.get() || matchingPatterns.isEmpty())
                return Collections.emptyList();

            findRevisionDynamically(context, matchingPatterns);
            XBundleRevision brev = context.targetRevision;
            if (brev == null)
                return Collections.emptyList();

            URL resURL = brev.getEntry(resName);
            if (resURL == null) {
                LOGGER.tracef("Cannot load resource [%s] from module: %s", resName, brev);
                return Collections.emptyList();
            }

            return Collections.singletonList((Resource) new URLResource(resURL));
        } finally {
            unlockFallbackLoader();
        }
    }

    private void findRevisionDynamically(DynamicLoadContext context, List<XPackageRequirement> matchingPatterns) {
        String pathName = context.resName;
        int idx = pathName.lastIndexOf('/');
        if (idx < 0)
            return;

        String path = pathName.substring(0, idx);
        if (importedPaths.contains(path))
            return;

        if (dynamicLoadAttempts == null)
            dynamicLoadAttempts = new ThreadLocal<Map<String, AtomicInteger>>();

        Map<String, AtomicInteger> mapping = dynamicLoadAttempts.get();
        boolean removeThreadLocalMapping = false;
        try {
            if (mapping == null) {
                mapping = new HashMap<String, AtomicInteger>();
                dynamicLoadAttempts.set(mapping);
                removeThreadLocalMapping = true;
            }

            AtomicInteger recursiveDepth = mapping.get(pathName);
            if (recursiveDepth == null)
                mapping.put(pathName, recursiveDepth = new AtomicInteger());

            if (recursiveDepth.incrementAndGet() == 1) {
                findInResolvedRevisions(context, matchingPatterns);
                if (context.targetRevision == null) {
                    findInUnresolvedRevisions(context, matchingPatterns);
                    if (context.targetRevision == null) {
                        findInSystemRevision(context, matchingPatterns);
                    }
                }
            }
        } finally {
            if (removeThreadLocalMapping == true) {
                dynamicLoadAttempts.remove();
            } else {
                AtomicInteger recursiveDepth = mapping.get(pathName);
                if (recursiveDepth.decrementAndGet() == 0)
                    mapping.remove(pathName);
            }
        }
    }

    private List<XPackageRequirement> findMatchingPatterns(String resName) {

        List<XPackageRequirement> dynamicRequirements = getDynamicPackageRequirements(hostRev);

        // Dynamic imports may not be used when the package is exported
        String pathName = VFSUtils.getPathFromClassName(resName);
        List<XPackageCapability> packageCapabilities = getPackageCapabilities(hostRev);
        for (XPackageCapability packageCap : packageCapabilities) {
            String packagePath = packageCap.getPackageName().replace('.', '/');
            if (pathName.equals(packagePath))
                return Collections.emptyList();
        }

        List<XPackageRequirement> foundMatch = new ArrayList<XPackageRequirement>();
        for (XPackageRequirement dynreq : dynamicRequirements) {

            final String pattern = dynreq.getPackageName();
            if (pattern.equals("*")) {
                foundMatch.add(dynreq);
                continue;
            }

            String patternPath = getPatternPath(pattern);
            if (pathName.startsWith(patternPath)) {
                foundMatch.add(dynreq);
                continue;
            }
        }

        if (foundMatch.isEmpty() == false)
            LOGGER.tracef("Found match for path [%s] with Dynamic-ImportPackage pattern: %s", resName, foundMatch);
        else
            LOGGER.tracef("Class [%s] does not match Dynamic-ImportPackage patterns", resName);

        return foundMatch;
    }

    private void findInResolvedRevisions(DynamicLoadContext context, List<XPackageRequirement> matchingPatterns) {
        LOGGER.tracef("Attempt to find path dynamically in resolved modules ...");
        Set<XBundle> resolved = bundleManager.getBundles(Bundle.RESOLVED | Bundle.ACTIVE);
        LOGGER.tracef("Resolved modules: %d", resolved.size());
        if (LOGGER.isTraceEnabled()) {
            for (Bundle bundle : resolved)
                LOGGER.tracef("   %s", bundle);
        }
        if (resolved.isEmpty())
            return;

        for (XPackageRequirement pkgreq : matchingPatterns) {
            Map<XBundleRevision, XPackageCapability> matches = new HashMap<XBundleRevision, XPackageCapability>();
            for (XBundle bundle : resolved) {
                if (bundle != hostBundle) {
                    XBundleRevision brev = bundle.getBundleRevision();
                    if (bundle.getBundleId() > 0 && !brev.isFragment() && matches.get(brev) == null) {
                        XPackageCapability bcap = getCapabilityCandidate(context, pkgreq, brev);
                        if (bcap != null) {
                            matches.put(brev, bcap);
                        }
                    }
                }
            }
            List<XBundleRevision> matchingRevisions = new ArrayList<XBundleRevision>(matches.keySet());
            if (matchingRevisions.size() > 0) {
                if (matchingRevisions.size() > 1) {
                    // Sort multiple revision candidates - highest version first
                    Collections.sort(matchingRevisions, new Comparator<XBundleRevision>() {
                        @Override
                        public int compare(XBundleRevision brevA, XBundleRevision brevB) {
                            if (brevA.getSymbolicName().equals(brevB.getSymbolicName())) {
                                return brevB.getVersion().compareTo(brevA.getVersion());
                            } else {
                                return (int) (brevB.getBundle().getBundleId() - brevA.getBundle().getBundleId());
                            }
                        }
                    });
                }
                XBundleRevision brev = matchingRevisions.get(0);
                context.capability = matches.get(brev);
                context.targetRevision = brev;
                context.requirement = pkgreq;
                return;
            }
        }
    }

    private boolean filterMatches(XPackageRequirement req, XPackageCapability cap) {

        // Cannot filter invalid types
        if (!(req instanceof BundleRequirement) || !(cap instanceof BundleCapability))
            return true;

        boolean callHookLifecycle = false;
        ResolverHookProcessor hookregs = ResolverHookProcessor.getCurrentProcessor();
        if (hookregs == null) {
            BundleContext syscontext = bundleManager.getSystemBundle().getBundleContext();
            hookregs = new ResolverHookProcessor(syscontext, null);
            callHookLifecycle = true;
        }

        // Nothing to filter
        if (hookregs.hasResolverHooks() == false)
            return true;

        Collection<BundleCapability> bcaps = new HashSet<BundleCapability>();
        bcaps.add((BundleCapability) cap);
        bcaps = new RemoveOnlyCollection<BundleCapability>(bcaps);

        if (callHookLifecycle) {
            try {
                hookregs.begin(Collections.singleton(hostRev), null);
                hookregs.filterMatches((BundleRequirement) req, bcaps);
            } finally {
                hookregs.end();
            }
        } else {
            hookregs.filterMatches((BundleRequirement) req, bcaps);
        }

        return bcaps.contains(cap);
    }

    private void findInUnresolvedRevisions(DynamicLoadContext context, List<XPackageRequirement> matchingPatterns) {
        LOGGER.tracef("Attempt to find path dynamically in unresolved modules ...");
        Set<XBundle> unresolved = bundleManager.getBundles(Bundle.INSTALLED);
        LOGGER.tracef("Unresolved modules: %d", unresolved.size());
        if (LOGGER.isTraceEnabled()) {
            for (Bundle bundle : unresolved)
                LOGGER.tracef("   %s", bundle);
        }
        for (Bundle bundle : unresolved) {
            if (!(bundle instanceof AbstractBundleState)) {
                LOGGER.tracef("Ignore invalid bundle type: %s", bundle);
                continue;
            }
            LOGGER.tracef("Attempt to resolve: %s", bundle);
            AbstractBundleState.assertBundleState(bundle).ensureResolved(false);
        }
        findInResolvedRevisions(context, matchingPatterns);
    }

    private void findInSystemRevision(DynamicLoadContext context, List<XPackageRequirement> matchingPatterns) {
        LOGGER.tracef("Attempt to find path dynamically in framework module ...");
        String resName = context.resName;
        int lastIndex = resName.lastIndexOf('/');
        String pathName = lastIndex > 0 ? resName.substring(0, lastIndex) : resName;
        SystemPaths systemPaths = frameworkState.getSystemPathsPlugin();
        if (systemPaths.getSystemPaths().contains(pathName)) {
            context.targetRevision = frameworkState.getSystemBundle().getBundleRevision();
        }
    }

    private XPackageCapability getCapabilityCandidate(DynamicLoadContext context, XPackageRequirement pkgreq, XBundleRevision brev) {

        // Skip dynamic loads from this module
        if (brev == hostRev)
            return null;

        LOGGER.tracef("Attempt to find path dynamically [%s] in %s ...", context.resName, brev);
        URL resURL = brev.getEntry(context.resName);
        if (resURL == null) {
            return null;
        }

        XPackageCapability cap = getCandidateCapability(brev, pkgreq);
        return (cap != null && filterMatches(pkgreq, cap) ? cap : null);
    }

    private XPackageCapability getCandidateCapability(BundleRevision brev, XPackageRequirement packageReq) {
        for (XPackageCapability packageCap : getPackageCapabilities(brev)) {
            if (packageReq.matches(packageCap)) {
                LOGGER.tracef("Matching package capability: %s", packageCap);
                return packageCap;
            }
        }
        return null;
    }

    private String getPatternPath(final String pattern) {

        String patternPath = pattern;
        if (pattern.endsWith(".*"))
            patternPath = pattern.substring(0, pattern.length() - 2);

        patternPath = patternPath.replace('.', '/');
        return patternPath;
    }

    private List<XPackageCapability> getPackageCapabilities(BundleRevision brev) {
        List<XPackageCapability> result = new ArrayList<XPackageCapability>();
        for (Capability aux : brev.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
            result.add(((XCapability) aux).adapt(XPackageCapability.class));
        }
        return result;
    }

    private List<XPackageRequirement> getDynamicPackageRequirements(BundleRevision brev) {

        List<XPackageRequirement> result = new ArrayList<XPackageRequirement>();
        for (Requirement aux : brev.getRequirements(PackageNamespace.PACKAGE_NAMESPACE)) {
            XRequirement xreq = (XRequirement) aux;
            XPackageRequirement preq = xreq.adapt(XPackageRequirement.class);
            if (preq.isDynamic()) {
                result.add(preq);
            }
        }

        // Add the dynamic package reqs generated by the {@link WeavingHook}s.
        if (weavingImports != null && !weavingImports.isEmpty())
            result.addAll(weavingImports);

        return result;
    }

    class DynamicLoadContext {

        final String resName;
        XBundleRevision targetRevision;
        XPackageRequirement requirement;
        XPackageCapability capability;

        DynamicLoadContext(String resName) {
            this.resName = resName;
        }
    }
}
