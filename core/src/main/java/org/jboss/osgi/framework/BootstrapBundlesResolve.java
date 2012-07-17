package org.jboss.osgi.framework;

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceBuilder.DependencyType;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.IntegrationServices.BootstrapPhase;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.Bundle;
import org.osgi.service.packageadmin.PackageAdmin;

public class BootstrapBundlesResolve<T> extends BootstrapBundlesService<T> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<PackageAdmin> injectedPackageAdmin = new InjectedValue<PackageAdmin>();
    private final Set<ServiceName> installedServices;

    public BootstrapBundlesResolve(ServiceName baseName, Set<ServiceName> installedServices) {
        super(baseName, BootstrapPhase.RESOLVE);
        this.installedServices = installedServices;
    }

    public ServiceController<T> install(ServiceTarget serviceTarget) {
        ServiceBuilder<T> builder = serviceTarget.addService(getServiceName(), this);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.PACKAGE_ADMIN, PackageAdmin.class, injectedPackageAdmin);
        builder.addDependencies(getPreviousService());
        for (ServiceName name : installedServices) {
            builder.addDependency(DependencyType.OPTIONAL, name);
        }
        builder.setInitialMode(Mode.ON_DEMAND);
        addServiceDependencies(builder);
        return builder.install();
    }

    protected void addServiceDependencies(ServiceBuilder<T> builder) {
    }

    @Override
    public void start(StartContext context) throws StartException {

        ServiceContainer serviceRegistry = context.getController().getServiceContainer();
        int targetLevel = getBeginningStartLevel();

        // Collect the set of resolvable bundles
        Map<ServiceName, XBundle> resolvableServices = new HashMap<ServiceName, XBundle>();
        for (ServiceName serviceName : installedServices) {
            ServiceController<?> controller = serviceRegistry.getRequiredService(serviceName);
            XBundle bundle = (XBundle) controller.getValue();
            Deployment dep = bundle.adapt(Deployment.class);
            int bundleLevel = dep.getStartLevel() != null ? dep.getStartLevel() : 1;
            if (dep.isAutoStart() && !bundle.isFragment() && bundleLevel <= targetLevel) {
                resolvableServices.put(serviceName, bundle);
            }
        }

        // Leniently resolve the bundles
        Bundle[] bundles = new Bundle[resolvableServices.size()];
        PackageAdmin packageAdmin = injectedPackageAdmin.getValue();
        packageAdmin.resolveBundles(resolvableServices.values().toArray(bundles));

        // Collect the resolved services
        final Set<ServiceName> resolvedServices = new HashSet<ServiceName>();
        for (Entry<ServiceName, XBundle> entry : resolvableServices.entrySet()) {
            if (entry.getValue().isResolved()) {
                ServiceName serviceName = entry.getKey().getParent().append("RESOLVED");
                resolvedServices.add(serviceName);
            }
        }

        installActivateService(context.getChildTarget(), resolvedServices);
    }

    private int getBeginningStartLevel() {
        BundleManager bundleManager = injectedBundleManager.getValue();
        String levelSpec = (String) bundleManager.getProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL);
        if (levelSpec != null) {
            try {
                return Integer.parseInt(levelSpec);
            } catch (NumberFormatException nfe) {
                LOGGER.errorInvalidBeginningStartLevel(levelSpec);
            }
        }
        return 1;
    }

    protected ServiceController<T> installActivateService(ServiceTarget serviceTarget, Set<ServiceName> resolvedServices) {
        return new BootstrapBundlesActivate<T>(getServiceName().getParent(), resolvedServices).install(serviceTarget);
    }

}