/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework.internal;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.Resource;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.spi.NotImplementedException;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.Bundle;

/**
 * A fallback loader that takes care of dynamic class/resource loads.
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 29-Jun-2010
 */
final class LegacyFallbackLoader implements LocalLoader {

    // Provide logging
    private static final Logger log = Logger.getLogger(LegacyFallbackLoader.class);

    private static ThreadLocal<Map<String, AtomicInteger>> dynamicLoadAttempts;
    private final HostBundleState hostBundle;
    private final ModuleIdentifier identifier;
    private final Set<String> importedPaths;

    LegacyFallbackLoader(HostBundleState hostBundle, ModuleIdentifier identifier, Set<String> importedPaths) {
        if (hostBundle == null)
            throw new IllegalArgumentException("Null hostBundle");
        if (identifier == null)
            throw new IllegalArgumentException("Null identifier");
        if (importedPaths == null)
            throw new IllegalArgumentException("Null importedPaths");
        this.identifier = identifier;
        this.importedPaths = importedPaths;
        this.hostBundle = hostBundle;
    }

    @Override
    public Class<?> loadClassLocal(String className, boolean resolve) {

        List<XPackageRequirement> matchingPatterns = findMatchingPatterns(className);
        if (matchingPatterns.isEmpty())
            return null;

        String pathName = className.replace('.', '/') + ".class";
        Module module = findModuleDynamically(pathName, matchingPatterns);
        if (module == null)
            return null;

        ModuleClassLoader moduleClassLoader = module.getClassLoader();
        try {
            return moduleClassLoader.loadClass(className);
        } catch (ClassNotFoundException ex) {
            log.tracef("Cannot load class [%s] from module: %s", className, module);
            return null;
        }
    }

    @Override
    public Package loadPackageLocal(String name) {
        throw new NotImplementedException();
    }

    @Override
    public List<Resource> loadResourceLocal(String resName) {

        if (resName.startsWith("/"))
            resName = resName.substring(1);

        List<XPackageRequirement> matchingPatterns = findMatchingPatterns(resName);
        if (matchingPatterns.isEmpty())
            return Collections.emptyList();

        Module module = findModuleDynamically(resName, matchingPatterns);
        if (module == null)
            return Collections.emptyList();

        URL resURL = module.getExportedResource(resName);
        if (resURL == null) {
            log.tracef("Cannot load resource [%s] from module: %s", resName, module);
            return Collections.emptyList();
        }

        return Collections.singletonList((Resource) new URLResource(resURL));
    }

    private Module findModuleDynamically(String resName, List<XPackageRequirement> matchingPatterns) {
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
                Module module = findInResolvedModules(resName, matchingPatterns);
                if (module != null && module.getIdentifier().equals(identifier) == false)
                    return module;

                module = findInUnresolvedModules(resName, matchingPatterns);
                if (module != null && module.getIdentifier().equals(identifier) == false)
                    return module;
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

        ModuleManagerPlugin moduleManager = hostBundle.getFrameworkState().getModuleManagerPlugin();
        AbstractBundleRevision bundleRev = moduleManager.getBundleRevision(identifier);
        XModule resModule = bundleRev.getResolverModule();
        List<XPackageRequirement> dynamicRequirements = resModule.getDynamicPackageRequirements();

        // Dynamic imports may not be used when the package is exported
        String pathName = VFSUtils.getPathFromClassName(resName);
        List<XPackageCapability> packageCapabilities = resModule.getPackageCapabilities();
        for (XPackageCapability packageCap : packageCapabilities) {
            String packagePath = packageCap.getName().replace('.', '/');
            if (pathName.equals(packagePath))
                return Collections.emptyList();
        }

        List<XPackageRequirement> foundMatch = new ArrayList<XPackageRequirement>();
        for (XPackageRequirement dynreq : dynamicRequirements) {

            final String pattern = dynreq.getName();
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
            log.tracef("Found match for path [%s] with Dynamic-ImportPackage pattern: %s", resName, foundMatch);
        else
            log.tracef("Class [%s] does not match Dynamic-ImportPackage patterns", resName);

        return foundMatch;
    }

    private Module findInResolvedModules(String resName, List<XPackageRequirement> matchingPatterns) {
        log.tracef("Attempt to find path dynamically in resolved modules ...");
        BundleManager bundleManager = hostBundle.getFrameworkState().getBundleManager();
        ModuleManagerPlugin moduleManager = hostBundle.getFrameworkState().getModuleManagerPlugin();
        for (XPackageRequirement packageReq : matchingPatterns) {
            for (AbstractBundleState bundleState : bundleManager.getBundles(Bundle.RESOLVED | Bundle.ACTIVE)) {
                if (bundleState.isResolved() && !bundleState.isFragment()) {
                    ModuleIdentifier identifier = bundleState.getModuleIdentifier();
                    Module candidate = moduleManager.getModule(identifier);
                    if (isValidCandidate(resName, packageReq, candidate))
                        return candidate;
                }
            }
        }
        return null;
    }

    private Module findInUnresolvedModules(String resName, List<XPackageRequirement> matchingPatterns) {
        log.tracef("Attempt to find path dynamically in unresolved modules ...");
        for (AbstractBundleState bundleState : hostBundle.getBundleManager().getBundles()) {
            if (bundleState.getState() == Bundle.INSTALLED) {
                bundleState.ensureResolved(false);
            }
        }
        return findInResolvedModules(resName, matchingPatterns);
    }

    private boolean isValidCandidate(String resName, XPackageRequirement packageReq, Module candidate) {

        if (candidate == null)
            return false;

        // Skip dynamic loads from this module
        ModuleIdentifier candidateId = candidate.getIdentifier();
        if (candidateId.equals(identifier))
            return false;

        log.tracef("Attempt to find path dynamically [%s] in %s ...", resName, candidateId);
        URL resURL = candidate.getExportedResource(resName);
        if (resURL == null)
            return false;

        log.tracef("Found path [%s] in %s", resName, candidate);
        ModuleManagerPlugin moduleManager = hostBundle.getFrameworkState().getModuleManagerPlugin();
        AbstractBundleRevision bundleRevision = moduleManager.getBundleRevision(candidateId);
        XModule resModule = bundleRevision.getResolverModule();

        XPackageCapability candidateCap = getCandidateCapability(resModule, packageReq);
        return (candidateCap != null);
    }

    private XPackageCapability getCandidateCapability(XModule resModule, XPackageRequirement packageReq) {
        for (XPackageCapability packageCap : resModule.getPackageCapabilities()) {
            if (packageReq.match(packageCap)) {
                log.tracef("Matching package capability: %s", packageCap);
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
}
