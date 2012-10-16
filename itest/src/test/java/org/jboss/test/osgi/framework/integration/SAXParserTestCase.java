package org.jboss.test.osgi.framework.integration;
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

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.net.URL;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A test that uses a SAX parser to read an XML document.
 *
 * @author thomas.diesler@jboss.com
 * @since 21-Jul-2009
 */
@RunWith(Arquillian.class)
public class SAXParserTestCase {

    @Deployment
    public static JavaArchive createdeployment() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "sax-parser");
        archive.addAsResource("jaxp/simple.xml");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(SAXParser.class, SAXException.class, DefaultHandler.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    @Test
    public void testSAXParser(@ArquillianResource Bundle bundle) throws Exception {

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);

        SAXParser saxParser = factory.newSAXParser();
        URL resURL = bundle.getResource("jaxp/simple.xml");

        SAXHandler saxHandler = new SAXHandler();
        saxParser.parse(resURL.openStream(), saxHandler);
        assertEquals("content", saxHandler.getContent());
    }

    static class SAXHandler extends DefaultHandler {
        private String content;

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            content = new String(ch, start, length);
        }

        public String getContent() {
            return content;
        }
    }
}