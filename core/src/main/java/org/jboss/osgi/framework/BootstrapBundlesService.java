package org.jboss.osgi.framework;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.osgi.framework.IntegrationServices.BootstrapPhase;

public class BootstrapBundlesService<T> extends AbstractService<T> {

    private final ServiceName baseName;
    private final BootstrapPhase phase;
    private ServiceRegistry serviceRegistry;

    public BootstrapBundlesService(ServiceName baseName, BootstrapPhase phase) {
        this.baseName = baseName;
        this.phase = phase;
    }

    public ServiceName getServiceName() {
        return BootstrapPhase.serviceName(baseName, phase);
    }

    public ServiceName getPreviousService() {
        return BootstrapPhase.serviceName(baseName, phase.previous());
    }

    public ServiceName getNextService() {
        return BootstrapPhase.serviceName(baseName, phase.next());
    }

    @Override
    public void start(StartContext context) throws StartException {
        serviceRegistry = context.getController().getServiceContainer();
    }

    protected void activateNextService() {
        ServiceController<?> controller = serviceRegistry.getRequiredService(getNextService());
        controller.setMode(Mode.ACTIVE);
    }
}