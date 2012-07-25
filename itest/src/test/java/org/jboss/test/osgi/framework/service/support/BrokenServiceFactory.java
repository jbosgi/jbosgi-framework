package org.jboss.test.osgi.framework.service.support;
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

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

/**
 * BrokenServiceFactory.
 *
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 1.1 $
 */
public class BrokenServiceFactory implements ServiceFactory {

    Object service;
    boolean inGet;

    public BrokenServiceFactory(Object service, boolean inGet) {
        this.service = service;
        this.inGet = inGet;
    }

    public Object getService(Bundle bundle, ServiceRegistration registration) {
        if (inGet)
            throw new RuntimeException("told to throw error");
        return service;
    }

    public void ungetService(Bundle bundle, ServiceRegistration registration, Object service) {
        if (inGet == false)
            throw new RuntimeException("told to throw error");
    }

}
