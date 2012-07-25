package org.jboss.test.osgi.framework.service.support.d;
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

import org.jboss.test.osgi.framework.service.support.a.A;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class ServiceMixFactory implements ServiceFactory {

    private List<Object> as = new ArrayList<Object>();

    public Object getService(Bundle bundle, ServiceRegistration registration) {
        A a = new A();
        a.msg = bundle.getSymbolicName();
        return a;
    }

    public void ungetService(Bundle bundle, ServiceRegistration registration, Object service) {
        as.add(service);
    }

    public List<Object> getAs() {
        return as;
    }
}
