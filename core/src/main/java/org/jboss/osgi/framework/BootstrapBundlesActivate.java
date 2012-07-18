package org.jboss.osgi.framework;

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.osgi.framework.IntegrationServices.BootstrapPhase;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public class BootstrapBundlesActivate<T> extends BootstrapBundlesService<T> {

    private final Set<ServiceName> resolvedServices;

    public BootstrapBundlesActivate(ServiceName baseName, Set<ServiceName> resolvedServices) {
        super(baseName, BootstrapPhase.ACTIVATE);
        this.resolvedServices = resolvedServices;
    }

    public ServiceController<T> install(ServiceTarget serviceTarget) {
        ServiceBuilder<T> builder = serviceTarget.addService(getServiceName(), this);
        builder.addDependencies(getPreviousService());
        addServiceDependencies(builder);
        return builder.install();
    }

    protected void addServiceDependencies(ServiceBuilder<T> builder) {
    }

    @Override
    public void start(StartContext context) throws StartException {
        ServiceContainer serviceRegistry = context.getController().getServiceContainer();

        // Collect the resolved bundles
        List<XBundle> bundles = new ArrayList<XBundle>();
        for (ServiceName serviceName : resolvedServices) {
            ServiceController<?> controller = serviceRegistry.getRequiredService(serviceName);
            bundles.add((XBundle) controller.getValue());
        }

        // Sort the bundles by Id
        Collections.sort(bundles, new Comparator<Bundle>(){
            public int compare(Bundle o1, Bundle o2) {
                return (int) (o1.getBundleId() - o2.getBundleId());
            }
        });

        // Start the resolved bundles
        for (XBundle bundle : bundles) {
            try {
                bundle.start(Bundle.START_ACTIVATION_POLICY);
            } catch (BundleException ex) {
                LOGGER.errorCannotStartBundle(ex, bundle);
            }
        }

        // We are done
        installCompleteService(context.getChildTarget());
    }

    protected ServiceController<T> installCompleteService(ServiceTarget serviceTarget) {
        return new BootstrapBundlesComplete<T>(getServiceName().getParent()).install(serviceTarget);
    }
}