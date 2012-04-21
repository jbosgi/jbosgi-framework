/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
