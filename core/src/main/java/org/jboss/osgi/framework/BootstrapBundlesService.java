package org.jboss.osgi.framework;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.IntegrationServices.BootstrapPhase;

public class BootstrapBundlesService<T> extends AbstractService<T> {

    private final ServiceName baseName;
    private final BootstrapPhase phase;

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
}