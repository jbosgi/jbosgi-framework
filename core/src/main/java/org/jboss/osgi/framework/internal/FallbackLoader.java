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
import java.util.Collections;
import java.util.HashMap;
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
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

/**
 * A fallback loader that takes care of dynamic class/resource loads.
 *
 * @author thomas.diesler@jboss.com
 * @since 24-Feb-2012
 */
final class FallbackLoader implements LocalLoader {

    private final ReentrantLock fallbackLoaderLock = new ReentrantLock();
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final HostBundleState hostBundle;
    private final HostBundleRevision hostRev;
    private final Set<String> importedPaths;
    private final FrameworkState frameworkState;
    private final BundleManager bundleManager;

    private static ThreadLocal<Map<String, AtomicInteger>> dynamicLoadAttempts;

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
            return enabled.getAndSet(flag);
        } finally {
            unlockFallbackLoader();
        }
    }

    void lockFallbackLoader() {
        fallbackLoaderLock.lock();
    }

    void unlockFallbackLoader() {
        fallbackLoaderLock.unlock();
    }

    @Override
    public Class<?> loadClassLocal(String className, boolean resolve) {
        XBundleRevision brev = null;
        try {
            lockFallbackLoader();

            List<XPackageRequirement> matchingPatterns = findMatchingPatterns(className);
            if (!enabled.get() || matchingPatterns.isEmpty())
                return null;

            String pathName = className.replace('.', '/') + ".class";
            brev = findRevisionDynamically(pathName, matchingPatterns);
        } finally {
            unlockFallbackLoader();
        }
        if (brev != null) {
            try {
                ModuleClassLoader moduleClassLoader = brev.getModuleClassLoader();
                return moduleClassLoader.loadClass(className);
            } catch (ClassNotFoundException ex) {
                LOGGER.tracef("Cannot load class [%s] from module: %s", className, brev);
                return null;
            }
        }
        return null;
    }

    @Override
    public Package loadPackageLocal(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Resource> loadResourceLocal(String resName) {
        XBundleRevision brev = null;
        try {
            lockFallbackLoader();

            if (resName.startsWith("/"))
                resName = resName.substring(1);

            List<XPackageRequirement> matchingPatterns = findMatchingPatterns(resName);
            if (!enabled.get() || matchingPatterns.isEmpty())
                return Collections.emptyList();

            brev = findRevisionDynamically(resName, matchingPatterns);
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

    private XBundleRevision findRevisionDynamically(String resName, List<XPackageRequirement> matchingPatterns) {
        int idx = resName.lastIndexOf('/');
        if (idx < 0)
            return null;

        String path = resName.substring(0, idx);
        if (importedPaths.contains(path))
            return null;

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

            AtomicInteger recursiveDepth = mapping.get(resName);
            if (recursiveDepth == null)
                mapping.put(resName, recursiveDepth = new AtomicInteger());

            if (recursiveDepth.incrementAndGet() == 1) {
                XBundleRevision brev = findInResolvedRevisions(resName, matchingPatterns);
                if (brev != null && brev != hostRev)
                    return brev;

                brev = findInUnresolvedRevisions(resName, matchingPatterns);
                if (brev != null && brev != hostRev)
                    return brev;

                brev = findInSystemRevision(resName, matchingPatterns);
                if (brev != null && brev != hostRev)
                    return brev;
            }
        } finally {
            if (removeThreadLocalMapping == true) {
                dynamicLoadAttempts.remove();
            } else {
                AtomicInteger recursiveDepth = mapping.get(resName);
                if (recursiveDepth.decrementAndGet() == 0)
                    mapping.remove(resName);
            }
        }
        return null;
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

    private XBundleRevision findInResolvedRevisions(String resName, List<XPackageRequirement> matchingPatterns) {
        LOGGER.tracef("Attempt to find path dynamically in resolved modules ...");
        Set<XBundle> resolved = bundleManager.getBundles(Bundle.RESOLVED | Bundle.ACTIVE);
        LOGGER.tracef("Resolved modules: %d", resolved.size());
        if (LOGGER.isTraceEnabled()) {
            for (Bundle bundle : resolved)
                LOGGER.tracef("   %s", bundle);
        }
        if (!resolved.isEmpty()) {
            for (XPackageRequirement pkgreq : matchingPatterns) {
                for (XBundle bundle : resolved) {
                    XBundleRevision brev = bundle.getBundleRevision();
                    if (bundle.getBundleId() > 0 && !brev.isFragment()) {
                        if (isValidCandidate(resName, pkgreq, brev))
                            return brev;
                    }
                }
            }
        }
        return null;
    }

    private XBundleRevision findInUnresolvedRevisions(String resName, List<XPackageRequirement> matchingPatterns) {
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
        return findInResolvedRevisions(resName, matchingPatterns);
    }

    private XBundleRevision findInSystemRevision(String resName, List<XPackageRequirement> matchingPatterns) {
        LOGGER.tracef("Attempt to find path dynamically in framework module ...");
        int lastIndex = resName.lastIndexOf('/');
        String pathName = lastIndex > 0 ? resName.substring(0, lastIndex) : resName;
        SystemPaths systemPaths = frameworkState.getSystemPathsPlugin();
        SystemBundleRevision brev = frameworkState.getSystemBundle().getBundleRevision();
        return systemPaths.getSystemPaths().contains(pathName) ? brev : null;
    }

    private boolean isValidCandidate(String resName, XPackageRequirement pkgreq, XBundleRevision brev) {

        // Skip dynamic loads from this module
        if (brev == hostRev)
            return false;

        LOGGER.tracef("Attempt to find path dynamically [%s] in %s ...", resName, brev);

        XPackageCapability candidateCap = getCandidateCapability(brev, pkgreq);
        return (candidateCap != null);
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
        return result;
    }
}
