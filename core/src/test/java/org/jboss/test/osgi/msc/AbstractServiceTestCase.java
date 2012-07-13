/*
 * #%L
 * JBossOSGi Framework Core
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
package org.jboss.test.osgi.msc;

import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.junit.After;
import org.junit.Before;

/**
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 19-Apr-2012
 */
public abstract class AbstractServiceTestCase {

    Logger log = Logger.getLogger(AbstractServiceTestCase.class);
    ServiceContainer serviceContainer;
    ServiceTarget serviceTarget;

    @Before
    public void setUp() {
        serviceContainer = ServiceContainer.Factory.create();
        serviceTarget = serviceContainer.subTarget();
    }

    @After
    public void setTearDown() {
        serviceContainer.shutdown();
        serviceTarget = null;
    }

    class ServiceA extends TestService {
    }

    class ServiceB extends TestService {
    }
    
    class TestService implements Service<String> {

        private String value;
        
        @Override
        public void start(StartContext context) throws StartException {
            ServiceName sname = context.getController().getName();
            log.infof("start: %s", sname);
            value = sname.getSimpleName();
        }

        @Override
        public void stop(StopContext context) {
            ServiceName sname = context.getController().getName();
            log.infof("stop: %s", sname);
            value = null;
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}
