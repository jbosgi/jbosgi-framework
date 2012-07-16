package org.jboss.osgi.framework;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.framework.IntegrationServices.BootstrapPhase;

public class BootstrapBundlesComplete<T> extends BootstrapBundlesService<T> {

    public BootstrapBundlesComplete(ServiceName baseName) {
        super(baseName, BootstrapPhase.COMPLETE);
    }

    public ServiceController<T> install(ServiceTarget serviceTarget, boolean withDependency) {
        ServiceBuilder<T> builder = serviceTarget.addService(getServiceName(), this);
        if (withDependency) {
            builder.addDependency(getPreviousService());
            builder.setInitialMode(Mode.NEVER);
        }
        addServiceDependencies(builder);
        return builder.install();
    }

    protected void addServiceDependencies(ServiceBuilder<T> builder) {
    }
}