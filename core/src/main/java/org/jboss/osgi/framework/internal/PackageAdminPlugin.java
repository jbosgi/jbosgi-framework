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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.FrameworkWiringLock;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.framework.spi.StartLevelPlugin;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.resolver.XResource;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Wire;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.packageadmin.RequiredBundle;
import org.osgi.service.resolver.ResolutionException;

/**
 * An implementation of the {@link PackageAdmin} service.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 06-Jul-2010
 */
public final class PackageAdminPlugin extends ExecutorServicePlugin<PackageAdminPlugin> implements PackageAdmin {

    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<FrameworkEventsPlugin> injectedFrameworkEvents = new InjectedValue<FrameworkEventsPlugin>();
    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private final InjectedValue<ModuleManagerPlugin> injectedModuleManager = new InjectedValue<ModuleManagerPlugin>();
    private final InjectedValue<ResolverPlugin> injectedResolver = new InjectedValue<ResolverPlugin>();
    private final InjectedValue<StartLevelPlugin> injectedStartLevel = new InjectedValue<StartLevelPlugin>();
    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();
    private ServiceRegistration registration;

    PackageAdminPlugin() {
        super(Services.PACKAGE_ADMIN, "PackageAdmin Refresh Thread");
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<PackageAdminPlugin> builder) {
        super.addServiceDependencies(builder);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.addDependency(Services.START_LEVEL, StartLevelPlugin.class, injectedStartLevel);
        builder.addDependency(InternalServices.FRAMEWORK_EVENTS_PLUGIN, FrameworkEventsPlugin.class, injectedFrameworkEvents);
        builder.addDependency(InternalServices.MODULE_MANGER_PLUGIN, ModuleManagerPlugin.class, injectedModuleManager);
        builder.addDependency(InternalServices.LOCK_MANAGER_PLUGIN, LockManager.class, injectedLockManager);
        builder.addDependency(Services.FRAMEWORK_CREATE, BundleContext.class, injectedSystemContext);
        builder.addDependency(Services.RESOLVER, ResolverPlugin.class, injectedResolver);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        BundleContext systemContext = injectedSystemContext.getValue();
        registration = systemContext.registerService(PackageAdmin.class.getName(), this, null);
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        registration.unregister();
    }

    @Override
    public PackageAdminPlugin getValue() {
        return this;
    }

    @Override
    public ExportedPackage[] getExportedPackages(Bundle bundle) {

        if (bundle == null)
            return getAllExportedPackages();

        if (!(bundle instanceof XBundle))
            return null;

        // A bundle is destroyed if it is no longer known to the system.
        // An uninstalled bundle can potentially live on if there are
        // other bundles depending on it. Only after a call to
        // {@link PackageAdmin#refreshPackages(Bundle[])} the bundle gets destroyed.
        if (getBundleManager().getBundleById(bundle.getBundleId()) == null)
            return null;

        List<ExportedPackage> result = new ArrayList<ExportedPackage>();
        XBundle bundleState = (XBundle) bundle;
        for (XBundleRevision brev : bundleState.getAllBundleRevisions()) {
            if (brev.getWiring() != null) {
                for (Capability cap : brev.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                    XCapability xcap = (XCapability) cap;
                    XPackageCapability packcap = xcap.adapt(XPackageCapability.class);
                    result.add(new ExportedPackageImpl(packcap));
                }
            }
        }

        // mandated by spec
        if (result.size() == 0)
            return null;

        return result.toArray(new ExportedPackage[result.size()]);
    }

    private ExportedPackage[] getAllExportedPackages() {
        List<ExportedPackage> result = new ArrayList<ExportedPackage>();
        for (Bundle bundle : getBundleManager().getBundles()) {
            ExportedPackage[] pkgs = getExportedPackages(bundle);
            if (pkgs != null)
                result.addAll(Arrays.asList(pkgs));
        }

        return result.toArray(new ExportedPackage[result.size()]);
    }

    @Override
    public ExportedPackage[] getExportedPackages(String name) {
        ExportedPackage[] pkgs = getExportedPackagesInternal(name);
        return pkgs.length == 0 ? null : pkgs;
    }

    // This implementation is flawed but the design of this API in PackageAdmin
    // is also flawed and from R5 deprecated so we're doing a best effort
    private ExportedPackage[] getExportedPackagesInternal(String name) {
        assert name != null : "Null name";

        Set<ExportedPackage> result = new HashSet<ExportedPackage>();
        XEnvironment env = injectedEnvironment.getValue();
        for (XResource res : env.getResources(XEnvironment.ALL_IDENTITY_TYPES)) {
            XBundleRevision brev = (XBundleRevision) res;
            BundleWiring wiring = brev.getWiring();
            if (wiring != null && !brev.isFragment()) {
                for (Capability cap : wiring.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                    XCapability xcap = (XCapability) cap;
                    XPackageCapability packcap = xcap.adapt(XPackageCapability.class);
                    if (packcap.getPackageName().equals(name)) {
                        result.add(new ExportedPackageImpl(packcap));
                    }
                }
            }
        }
        return result.toArray(new ExportedPackage[result.size()]);
    }

    @Override
    public ExportedPackage getExportedPackage(String name) {
        ExportedPackage[] exported = getExportedPackagesInternal(name);
        List<ExportedPackage> wired = new ArrayList<ExportedPackage>();
        List<ExportedPackage> notWired = new ArrayList<ExportedPackage>();

        for (ExportedPackage ep : exported) {
            XPackageCapability cap = ((ExportedPackageImpl) ep).getCapability();
            if (isWired(cap))
                wired.add(ep);
            else
                notWired.add(ep);
        }
        ExportedPackageComparator comparator = new ExportedPackageComparator();
        Collections.sort(wired, comparator);
        Collections.sort(notWired, comparator);

        if (wired.size() > 0)
            return wired.get(0);
        else if (notWired.size() > 0)
            return notWired.get(0);
        else
            return null;
    }

    private boolean isWired(XPackageCapability cap) {
        BundleWiring wiring = ((BundleRevision) cap.getResource()).getWiring();
        if (wiring != null) {
            for (BundleWire wire : wiring.getProvidedWires(cap.getNamespace())) {
                if (cap.equals(wire.getCapability()))
                    return true;
            }
            List<BundleWire> requireBundleWires = wiring.getProvidedWires(BundleNamespace.BUNDLE_NAMESPACE);
            if (requireBundleWires.size() > 0)
                return true;
        }
        return false;
    }

    @Override
    public void refreshPackages(final Bundle[] bundles) {

        final List<Bundle> bundlesToRefresh = new ArrayList<Bundle>();
        if (bundles != null) {
            bundlesToRefresh.addAll(Arrays.asList(bundles));
        }

        Runnable runner = new Runnable() {
            @Override
            public void run() {
                
                FrameworkEventsPlugin eventsPlugin = injectedFrameworkEvents.getValue();
                
                LockContext lockContext = null;
                LockManager lockManager = injectedLockManager.getValue();
                try {
                    FrameworkWiringLock wireLock = lockManager.getItemForType(FrameworkWiringLock.class);
                    lockContext = lockManager.lockItems(Method.REFRESH, wireLock);
                    
                    if (bundles == null) {
                        // 4.2 core spec 7.5.3.11 on null:
                        // all bundles updated or uninstalled since the last call to this method.

                        for (Bundle bundle : getBundleManager().getBundles(null)) {
                            if (bundle.getBundleId() != 0) {
                                if (bundle instanceof UserBundleState) {
                                    UserBundleState userBundle = (UserBundleState) bundle;
                                    // a bundle with more than 1 revision has been updated since the last refresh packages call
                                    if (userBundle.getAllBundleRevisions().size() > 1 || bundle.getState() == Bundle.UNINSTALLED)
                                        bundlesToRefresh.add(userBundle);
                                }
                            }
                        }
                    }

                    Set<UserBundleState> providedBundles = new LinkedHashSet<UserBundleState>();
                    for (Bundle bundle : bundlesToRefresh) {
                        if (bundle instanceof UserBundleState) {
                            UserBundleState bundleState = UserBundleState.assertBundleState(bundle);
                            providedBundles.add(bundleState);
                        }
                    }

                    Set<HostBundleState> stopBundles = new HashSet<HostBundleState>();
                    Set<UserBundleState> refreshBundles = new HashSet<UserBundleState>();
                    Set<UserBundleState> uninstallBundles = new HashSet<UserBundleState>();

                    for (UserBundleState userBundle : providedBundles) {
                        if (userBundle.getState() == Bundle.UNINSTALLED)
                            uninstallBundles.add(userBundle);
                        else if (userBundle.isResolved() == true)
                            refreshBundles.add(userBundle);
                    }

                    // Compute all depending bundles that need to be stopped and unresolved.
                    for (XBundle bundle : getBundleManager().getBundles(Bundle.RESOLVED | Bundle.ACTIVE)) {
                        if (bundle instanceof HostBundleState) {
                            HostBundleState hostBundle = HostBundleState.assertBundleState(bundle);
                            for (UserBundleState depBundle : hostBundle.getDependentBundles()) {
                                if (providedBundles.contains(depBundle)) {
                                    int state = hostBundle.getState();
                                    if (state == Bundle.ACTIVE || state == Bundle.STARTING) {
                                        stopBundles.add(hostBundle);
                                    }
                                    refreshBundles.add(hostBundle);
                                    break;
                                }
                            }
                        }
                    }

                    // Add relevant bundles to be refreshed also to the stop list.
                    for (UserBundleState aux : refreshBundles) {
                        if (aux instanceof HostBundleState) {
                            int state = aux.getState();
                            if (state == Bundle.ACTIVE || state == Bundle.STARTING) {
                                stopBundles.add((HostBundleState) aux);
                            }
                        }
                    }

                    List<HostBundleState> stopList = new ArrayList<HostBundleState>(stopBundles);
                    List<UserBundleState> refreshList = new ArrayList<UserBundleState>(refreshBundles);

                    StartLevelPlugin startLevelPlugin = injectedStartLevel.getValue();
                    BundleStartLevelComparator startLevelComparator = new BundleStartLevelComparator(startLevelPlugin);
                    Collections.sort(stopList, startLevelComparator);

                    for (ListIterator<HostBundleState> it = stopList.listIterator(stopList.size()); it.hasPrevious();) {
                        HostBundleState hostBundle = it.previous();
                        try {
                            hostBundle.stop(Bundle.STOP_TRANSIENT);
                        } catch (Exception th) {
                            eventsPlugin.fireFrameworkEvent(hostBundle, FrameworkEvent.ERROR, th);
                        }
                    }

                    for (UserBundleState userBundle : uninstallBundles) {
                        getBundleManager().removeBundle(userBundle, 0);
                    }

                    for (UserBundleState userBundle : refreshList) {
                        try {
                            userBundle.refresh();
                        } catch (Exception th) {
                            eventsPlugin.fireFrameworkEvent(userBundle, FrameworkEvent.ERROR, th);
                        }
                    }

                    for (HostBundleState hostBundle : stopList) {
                        try {
                            hostBundle.start(Bundle.START_TRANSIENT);
                        } catch (Exception th) {
                            eventsPlugin.fireFrameworkEvent(hostBundle, FrameworkEvent.ERROR, th);
                        }
                    }
                } finally {
                    lockManager.unlockItems(lockContext);
                }
                
                eventsPlugin.fireFrameworkEvent(getBundleManager().getSystemBundle(), FrameworkEvent.PACKAGES_REFRESHED, null);
            }
        };

        //enableImmediateExecution(true);
        ExecutorService executorService = getExecutorService();
        if (!executorService.isShutdown()) {
            executorService.execute(runner);
        }
    }

    @Override
    public boolean resolveBundles(Bundle[] bundles) {
        // Only bundles that are in state INSTALLED and are
        // registered with the resolver qualify as resolvable
        ResolverPlugin resolverPlugin = injectedResolver.getValue();
        if (bundles == null) {
            Set<XBundle> bundleset = getBundleManager().getBundles(Bundle.INSTALLED);
            bundles = new Bundle[bundleset.size()];
            bundleset.toArray(bundles);
        }
        Set<BundleRevision> resolve = new LinkedHashSet<BundleRevision>();
        for (Bundle aux : bundles) {
            XBundle bundleState = (XBundle) aux;
            resolve.add(bundleState.getBundleRevision());
        }

        boolean result = true;
        try {
            resolverPlugin.resolveAndApply(resolve, null);
            for (BundleRevision aux : resolve) {
                if (aux.getWiring() == null) {
                    result = false;
                    break;
                }
            }
        } catch (ResolutionException ex) {
            LOGGER.debugf(ex, "Cannot resolve: " + resolve);
            result = false;
        }
        return result;
    }

    @Override
    public RequiredBundle[] getRequiredBundles(String symbolicName) {

        List<HostBundleState> matchingHosts = new ArrayList<HostBundleState>();
        if (symbolicName != null) {
            for (Bundle aux : getBundleManager().getBundles(symbolicName, null)) {
                if (aux instanceof HostBundleState)
                    matchingHosts.add((HostBundleState) aux);
            }
        } else {
            for (Bundle aux : getBundleManager().getBundles()) {
                if (aux instanceof HostBundleState)
                    matchingHosts.add((HostBundleState) aux);
            }
        }
        if (matchingHosts.isEmpty()) {
            return null;
        }

        List<RequiredBundle> result = new ArrayList<RequiredBundle>();
        Iterator<HostBundleState> hostit = matchingHosts.iterator();
        while (hostit.hasNext()) {
            HostBundleState hostState = hostit.next();
            XBundleRevision brev = hostState.getBundleRevision();
            Set<XBundle> requiringBundles = new HashSet<XBundle>();
            BundleWiring wiring = brev.getWiring();
            if (wiring != null) {
                List<Wire> providedWires = wiring.getProvidedResourceWires(BundleNamespace.BUNDLE_NAMESPACE);
                for (Wire wire : providedWires) {
                    XBundle bundle = ((XBundleRevision) wire.getRequirer()).getBundle();
                    requiringBundles.add(bundle);
                }
            }
            result.add(new RequiredBundleImpl(hostState, brev, requiringBundles));
        }
        return result.toArray(new RequiredBundle[matchingHosts.size()]);
    }

    /**
     * Returns the bundles with the specified symbolic name whose bundle version is within the specified version range. If no
     * bundles are installed that have the specified symbolic name, then <code>null</code> is returned. If a version range is
     * specified, then only the bundles that have the specified symbolic name and whose bundle versions belong to the specified
     * version range are returned. The returned bundles are ordered by version in descending version order so that the first
     * element of the array contains the bundle with the highest version.
     *
     * @param symbolicName The symbolic name of the desired bundles.
     * @param versionRange The version range of the desired bundles, or <code>null</code> if all versions are desired.
     * @return An array of bundles with the specified name belonging to the specified version range ordered in descending
     *         version order, or <code>null</code> if no bundles are found.
     */
    @Override
    public Bundle[] getBundles(final String symbolicName, final String versionRange) {
        Set<Bundle> sortedSet = new TreeSet<Bundle>(new Comparator<Bundle>() {
            // Makes sure that the bundles are sorted correctly in the returned array
            // Matching bundles with the highest version should come first.
            @Override
            public int compare(Bundle b1, Bundle b2) {
                if (symbolicName == null) {
                    return (int) (b1.getBundleId() - b2.getBundleId());
                } else {
                    return b2.getVersion().compareTo(b1.getVersion());
                }
            }
        });
        for (Bundle bundleState : getBundleManager().getBundles(symbolicName, versionRange)) {
            if (bundleState.getState() != Bundle.UNINSTALLED) {
                sortedSet.add(bundleState);
            }
        }
        if (sortedSet.isEmpty())
            return null;

        return sortedSet.toArray(new Bundle[sortedSet.size()]);
    }

    @Override
    public Bundle[] getFragments(Bundle bundle) {

        if (!(bundle instanceof HostBundleState))
            return null;

        // If the specified bundle is a fragment then null is returned.
        // If the specified bundle is not resolved then null is returned
        HostBundleState hostBundle = HostBundleState.assertBundleState(bundle);
        if (bundle.getBundleId() == 0 || !hostBundle.isResolved())
            return null;

        List<Bundle> result = new ArrayList<Bundle>();
        HostBundleRevision curRevision = hostBundle.getBundleRevision();
        for (FragmentBundleRevision aux : curRevision.getAttachedFragments())
            result.add(aux.getBundle());

        if (result.isEmpty())
            return null;

        return result.toArray(new Bundle[result.size()]);
    }

    @Override
    public Bundle[] getHosts(Bundle bundle) {

        if (!(bundle instanceof FragmentBundleState))
            return null;

        FragmentBundleState fragBundle = FragmentBundleState.assertBundleState(bundle);
        FragmentBundleRevision curRevision = fragBundle.getBundleRevision();

        List<Bundle> result = new ArrayList<Bundle>();
        for (HostBundleRevision aux : curRevision.getAttachedHosts())
            result.add(aux.getBundle());

        if (result.isEmpty())
            return null;

        return result.toArray(new Bundle[result.size()]);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Bundle getBundle(Class clazz) {
        ModuleManagerPlugin moduleManager = injectedModuleManager.getValue();
        XBundle bundleState = moduleManager.getBundleState(clazz);
        return bundleState != null ? bundleState : null;
    }

    @Override
    public int getBundleType(Bundle bundle) {
        XBundleRevision brev = ((XBundle) bundle).getBundleRevision();
        return brev.isFragment() ? BUNDLE_TYPE_FRAGMENT : 0;
    }

    static class ExportedPackageImpl implements ExportedPackage {

        private final XPackageCapability capability;

        ExportedPackageImpl(XPackageCapability cap) {
            capability = cap;
        }

        @Override
        public String getName() {
            return capability.getPackageName();
        }

        @Override
        public Bundle getExportingBundle() {
            return ((BundleRevision) capability.getResource()).getBundle();
        }

        @Override
        public Bundle[] getImportingBundles() {
            if (isRemovalPending())
                return null;

            Set<Bundle> bundles = new HashSet<Bundle>();
            XBundleRevision resource = (XBundleRevision) capability.getResource();
            BundleWiring wiring = resource.getWiring();
            for (BundleWire wire : wiring.getProvidedWires(PackageNamespace.PACKAGE_NAMESPACE)) {
                BundleRevision req = wire.getRequirer();
                bundles.add(req.getBundle());
            }
            for (BundleWire wire : wiring.getProvidedWires(BundleNamespace.BUNDLE_NAMESPACE)) {
                BundleRevision req = wire.getRequirer();
                bundles.add(req.getBundle());
            }

            return bundles.toArray(new Bundle[bundles.size()]);
        }

        @Override
        public String getSpecificationVersion() {
            return capability.getVersion().toString();
        }

        @Override
        public Version getVersion() {
            return capability.getVersion();
        }

        @Override
        public boolean isRemovalPending() {
            XBundleRevision brev = (XBundleRevision) capability.getResource();
            XBundle bundle = brev.getBundle();
            if (bundle instanceof AbstractBundleState && ((AbstractBundleState) bundle).isUninstalled()) {
                return true;
            }
            return brev != bundle.getBundleRevision();
        }

        private XPackageCapability getCapability() {
            return capability;
        }

        @Override
        public String toString() {
            return "ExportedPackage[" + capability + "]";
        }
    }

    static class RequiredBundleImpl implements RequiredBundle {

        private final XBundle requiredBundle;
        private final XBundle[] requiringBundles;
        private final XBundleRevision bundleRevision;

        RequiredBundleImpl(XBundle requiredBundle, XBundleRevision bundleRevision, Collection<XBundle> requiringBundles) {
            this.requiredBundle = requiredBundle;
            this.bundleRevision = bundleRevision;

            List<Bundle> bundles = new ArrayList<Bundle>(requiringBundles.size());
            for (Bundle bundle : requiringBundles) {
                bundles.add(bundle);
            }
            this.requiringBundles = bundles.toArray(new XBundle[bundles.size()]);
        }

        @Override
        public String getSymbolicName() {
            return requiredBundle.getSymbolicName();
        }

        @Override
        public Bundle getBundle() {
            if (isRemovalPending())
                return null;

            return requiredBundle;
        }

        @Override
        public Bundle[] getRequiringBundles() {
            if (isRemovalPending())
                return null;

            return requiringBundles;
        }

        @Override
        public Version getVersion() {
            return requiredBundle.getVersion();
        }

        @Override
        public boolean isRemovalPending() {
            if (requiredBundle.getState() == Bundle.UNINSTALLED)
                return true;

            XBundle bundle = bundleRevision.getBundle();
            return !bundleRevision.equals(bundle.getBundleRevision());
        }

        @Override
        public String toString() {
            return "RequiredBundle[" + requiredBundle + "]";
        }
    }

    private static class BundleStartLevelComparator implements Comparator<HostBundleState> {
        private final StartLevelPlugin startLevelPlugin;

        BundleStartLevelComparator(StartLevelPlugin startLevelPlugin) {
            this.startLevelPlugin = startLevelPlugin;
        }

        @Override
        public int compare(HostBundleState o1, HostBundleState o2) {
            int sl1 = startLevelPlugin.getBundleStartLevel(o1);
            int sl2 = startLevelPlugin.getBundleStartLevel(o2);
            return sl1 < sl2 ? -1 : (sl1 == sl2 ? 0 : 1);
        }
    }

    private static class ExportedPackageComparator implements Comparator<ExportedPackage> {

        @Override
        public int compare(ExportedPackage ep1, ExportedPackage ep2) {
            return ep2.getVersion().compareTo(ep1.getVersion());
        }
    }
}
