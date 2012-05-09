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
package org.jboss.test.osgi.framework.classloader.support;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class JavaXMLParserActivator implements BundleActivator {

    static final String TEST_XML = "<?xml version='1.0' encoding='UTF-8'?>"
            + "<project>"
            + "<modelVersion>4.0.0</modelVersion>"
            + "<groupId>foo.bar</groupId>"
            + "<artifactId>classloadingtest</artifactId>"
            + "<version>1.0-SNAPSHOT</version>"
            + "<packaging>bundle</packaging>"
            + "<name>classloadingtest</name>"
            + "</project>";

    @Override
    public void start(BundleContext context) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        ByteArrayInputStream input = new ByteArrayInputStream(TEST_XML.getBytes());
        saxParser.parse(input, new Myhandler());
    }

    @Override
    public void stop(BundleContext context) throws Exception {
    }

    static class Myhandler extends DefaultHandler {

        int starts = 0;
        int ends = 0;

        @Override
        public void startElement(String string, String string1, String string2, Attributes atrbts) throws SAXException {
            starts++;
        }

        @Override
        public void endElement(String string, String string1, String string2) throws SAXException {
            ends++;
        }
    }
}
