package org.jboss.osgi.framework;

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.ServiceController.Mode;
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
        builder.setInitialMode(Mode.NEVER);
        return builder.install();
    }

    protected void addServiceDependencies(ServiceBuilder<T> builder) {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);

        // Start the resolved bundles
        ServiceContainer serviceRegistry = context.getController().getServiceContainer();
        for (ServiceName serviceName : resolvedServices) {
            ServiceController<?> controller = serviceRegistry.getRequiredService(serviceName);
            XBundle bundle = (XBundle) controller.getValue();
            try {
                bundle.start(Bundle.START_ACTIVATION_POLICY);
            } catch (BundleException ex) {
                LOGGER.errorCannotStartBundle(ex, bundle);
            }
        }

        // We are done
        activateNextService();
    }
}