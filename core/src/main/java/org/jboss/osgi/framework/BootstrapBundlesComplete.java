package org.jboss.osgi.framework;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

public class BootstrapBundlesComplete extends AbstractService<Void> {

    private final ServiceName serviceName;

    public BootstrapBundlesComplete(ServiceName serviceName) {
        this.serviceName = serviceName;
    }

    public void install(ServiceTarget serviceTarget) {
        ServiceBuilder<Void> builder = serviceTarget.addService(serviceName, this);
        addServiceDependencies(builder);
        builder.install();
    }

    protected void addServiceDependencies(ServiceBuilder<Void> builder) {
    }
}