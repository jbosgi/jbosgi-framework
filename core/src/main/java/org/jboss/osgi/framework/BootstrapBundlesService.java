package org.jboss.osgi.framework;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

abstract class BootstrapBundlesService extends AbstractService<Void> {

    private final ServiceName serviceName;

    public BootstrapBundlesService(ServiceName serviceName) {
        this.serviceName = serviceName;
    }

    public ServiceName getServiceName() {
        return serviceName;
    }

    protected void installCompleteService(ServiceTarget serviceTarget) {
        new BootstrapBundlesComplete().install(serviceTarget, serviceName.getParent().append("COMPLETE"));
    }
}