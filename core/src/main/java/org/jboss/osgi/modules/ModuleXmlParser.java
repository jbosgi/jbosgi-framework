/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.jboss.osgi.modules;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.ExportFilterable;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ModuleSpec.Builder;
import org.jboss.modules.ResourceLoader;

/**
 * A fast, validating module.xml parser.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author thomas.diesler@jboss.com
 */
public final class ModuleXmlParser {

    private ModuleXmlParser() {
    }

    private static final String NAMESPACE = "urn:jboss:module:1.0";

    enum Element {
        MODULE,
        DEPENDENCIES,
        EXPORTS,
        INCLUDE,
        EXCLUDE,
        RESOURCES,
        MAIN_CLASS,
        RESOURCE_ROOT,

        // default unknown element
        UNKNOWN;

        private static final Map<QName, Element> elements;

        static {
            Map<QName, Element> elementsMap = new HashMap<QName, Element>();
            elementsMap.put(new QName(NAMESPACE, "module"), Element.MODULE);
            elementsMap.put(new QName(NAMESPACE, "dependencies"), Element.DEPENDENCIES);
            elementsMap.put(new QName(NAMESPACE, "resources"), Element.RESOURCES);
            elementsMap.put(new QName(NAMESPACE, "main-class"), Element.MAIN_CLASS);
            elementsMap.put(new QName(NAMESPACE, "resource-root"), Element.RESOURCE_ROOT);
            elementsMap.put(new QName(NAMESPACE, "exports"), Element.EXPORTS);
            elementsMap.put(new QName(NAMESPACE, "include"), Element.INCLUDE);
            elementsMap.put(new QName(NAMESPACE, "exclude"), Element.EXCLUDE);
            elements = elementsMap;
        }

        static Element of(QName qName) {
            final Element element = elements.get(qName);
            return element == null ? UNKNOWN : element;
        }
    }

    enum Attribute {
        GROUP,
        NAME,
        VERSION,
        EXPORT,
        PATH,
        FLAGS,
        OPTIONAL,
        
        // default unknown attribute
        UNKNOWN;

        private static final Map<QName, Attribute> attributes;

        static {
            Map<QName, Attribute> attributesMap = new HashMap<QName, Attribute>();
            attributesMap.put(new QName("group"), GROUP);
            attributesMap.put(new QName("name"), NAME);
            attributesMap.put(new QName("version"), VERSION);
            attributesMap.put(new QName("export"), EXPORT);
            attributesMap.put(new QName("path"), PATH);
            attributesMap.put(new QName("flags"), FLAGS);
            attributesMap.put(new QName("optional"), OPTIONAL);
            attributes = attributesMap;
        }

        static Attribute of(QName qName) {
            final Attribute attribute = attributes.get(qName);
            return attribute == null ? UNKNOWN : attribute;
        }
    }

    /**
     * A factory for a ResourceLoader
     */
    public interface ResourceLoaderFactory
    {
       ResourceLoader getResourceLoader(String path, String name) throws IOException;
    }
    
    public static ModuleSpec parse(final ResourceLoaderFactory factory, final InputStream inputStream) throws ModuleLoadException {
       try {
           return parse(factory, inputStream, null, null);
       } finally {
           safeClose(inputStream);
       }
    }
    
    public static ModuleSpec parse(final File root, final File moduleInfoFile) throws ModuleLoadException {
       return parse(null, root, moduleInfoFile);
    }
    
    static ModuleSpec parse(final ModuleIdentifier identifier, final File root, final File moduleInfoFile) throws ModuleLoadException {
       
        // A file based ResourceLoaderFactory
        ResourceLoaderFactory factory = new ResourceLoaderFactory()
        {
           @Override
           public ResourceLoader getResourceLoader(String path, String name) throws IOException
           {
              File file = new File(root, path);
              if (file.isDirectory())
              {
                 return new FileResourceLoader(identifier, file, name);
              }
              else
              {
                 return new JarFileResourceLoader(identifier, new JarFile(file), name);
              }
           }
        };
        
        final FileInputStream fis;
        try {
            fis = new FileInputStream(moduleInfoFile);
        } catch (FileNotFoundException e) {
            throw new ModuleLoadException("No module.xml file found at " + moduleInfoFile);
        }
        try {
            return parse(factory, fis, moduleInfoFile, identifier);
        } finally {
            safeClose(fis);
        }
    }

    private static void setIfSupported(XMLInputFactory inputFactory, String property, Object value) {
        if (inputFactory.isPropertySupported(property)) {
            inputFactory.setProperty(property, value);
        }
    }

    private static ModuleSpec parse(final ResourceLoaderFactory factory, InputStream source, final File moduleInfoFile, final ModuleIdentifier identifier) throws ModuleLoadException {
        try {
            final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            setIfSupported(inputFactory, XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
            setIfSupported(inputFactory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            final XMLStreamReader streamReader = inputFactory.createXMLStreamReader(source);
            try {
                Builder builder = (identifier != null ? ModuleSpec.build(identifier) : null);
                return parseDocument(factory, streamReader, builder);
            } finally {
                safeClose(streamReader);
            }
        } catch (XMLStreamException e) {
            throw new ModuleLoadException("Error loading module from " + moduleInfoFile, e);
        }
    }

    private static void safeClose(final Closeable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private static void safeClose(final XMLStreamReader streamReader) {
        if (streamReader != null) try {
            streamReader.close();
        } catch (XMLStreamException e) {
            // ignore
        }
    }

    private static XMLStreamException unexpectedContent(final XMLStreamReader reader) {
        final String kind;
        switch (reader.getEventType()) {
            case XMLStreamConstants.ATTRIBUTE: kind = "attribute"; break;
            case XMLStreamConstants.CDATA: kind = "cdata"; break;
            case XMLStreamConstants.CHARACTERS: kind = "characters"; break;
            case XMLStreamConstants.COMMENT: kind = "comment"; break;
            case XMLStreamConstants.DTD: kind = "dtd"; break;
            case XMLStreamConstants.END_DOCUMENT: kind = "document end"; break;
            case XMLStreamConstants.END_ELEMENT: kind = "element end"; break;
            case XMLStreamConstants.ENTITY_DECLARATION: kind = "entity decl"; break;
            case XMLStreamConstants.ENTITY_REFERENCE: kind = "entity ref"; break;
            case XMLStreamConstants.NAMESPACE: kind = "namespace"; break;
            case XMLStreamConstants.NOTATION_DECLARATION: kind = "notation decl"; break;
            case XMLStreamConstants.PROCESSING_INSTRUCTION: kind = "processing instruction"; break;
            case XMLStreamConstants.SPACE: kind = "whitespace"; break;
            case XMLStreamConstants.START_DOCUMENT: kind = "document start"; break;
            case XMLStreamConstants.START_ELEMENT: kind = "element start"; break;
            default: kind = "unknown"; break;
        }
        final StringBuilder b = new StringBuilder("Unexpected content of type '").append(kind).append('\'');
        if (reader.hasName()) {
            b.append(" named '").append(reader.getName()).append('\'');
        }
        if (reader.hasText()) {
            b.append(", text is: '").append(reader.getText()).append('\'');
        }
        return new XMLStreamException(b.toString(), reader.getLocation());
    }

    private static XMLStreamException unknownFlag(final String flag, final Location location) {
        return new XMLStreamException("Invalid flag \"" + flag + "\" specified", location);
    }

    private static XMLStreamException endOfDocument(final Location location) {
        return new XMLStreamException("Unexpected end of document", location);
    }

    private static XMLStreamException invalidModuleName(final Location location, final ModuleIdentifier expected) {
        return new XMLStreamException("Invalid/mismatched module name (expected " + expected + ")", location);
    }

    private static XMLStreamException missingAttributes(final Location location, final Set<Attribute> required) {
        final StringBuilder b = new StringBuilder("Missing one or more required attributes:");
        for (Attribute attribute : required) {
            b.append(' ').append(attribute);
        }
        return new XMLStreamException(b.toString(), location);
    }

    private static ModuleSpec parseDocument(final ResourceLoaderFactory factory, XMLStreamReader reader, ModuleSpec.Builder specBuilder) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_DOCUMENT: {
                    specBuilder = parseRootElement(factory, reader, specBuilder);
                    return specBuilder.create();
                }
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.MODULE) {
                        throw unexpectedContent(reader);
                    }
                    specBuilder = parseModuleContents(factory, reader, specBuilder);
                    parseEndDocument(reader);
                    return specBuilder.create();
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static ModuleSpec.Builder parseRootElement(final ResourceLoaderFactory factory, final XMLStreamReader reader, ModuleSpec.Builder specBuilder) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_ELEMENT: {
                    if (Element.of(reader.getName()) != Element.MODULE) {
                        throw unexpectedContent(reader);
                    }
                    specBuilder = parseModuleContents(factory, reader, specBuilder);
                    parseEndDocument(reader);
                    return specBuilder;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static ModuleSpec.Builder parseModuleContents(final ResourceLoaderFactory factory, final XMLStreamReader reader, ModuleSpec.Builder specBuilder) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        String flags = null;
        String name = null;
        String group = null;
        String version = null;
        final Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.GROUP);
        for (int i = 0; i < count; i ++) {
            final Attribute attribute = Attribute.of(reader.getAttributeName(i));
            required.remove(attribute);
            switch (attribute) {
                case FLAGS:   flags = reader.getAttributeValue(i); break;
                case GROUP:   group = reader.getAttributeValue(i); break;
                case NAME:    name = reader.getAttributeValue(i); break;
                case VERSION: version = reader.getAttributeValue(i); break;
                default: throw unexpectedContent(reader);
            }
        }
        if (! required.isEmpty()) {
            throw missingAttributes(reader.getLocation(), required);
        }
        ModuleIdentifier identifier = new ModuleIdentifier(group, name, version);
        if (specBuilder == null) {
           specBuilder = ModuleSpec.build(identifier);  
        }
        if (specBuilder.getIdentifier().equals(identifier) == false) {
            throw invalidModuleName(reader.getLocation(), specBuilder.getIdentifier());
        }
        if (flags != null) {
            for (String flag : flags.split("\\s+")) {
                try {
                    final Module.Flag flagVal = Module.Flag.valueOf(flag.toUpperCase().replace('-', '_'));
                    specBuilder.addModuleFlag(flagVal);
                } catch (IllegalArgumentException e) {
                    throw unknownFlag(flag, reader.getLocation());
                }
            }
        }
        // xsd:all
        Set<Element> visited = EnumSet.noneOf(Element.class);
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    return specBuilder;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    final Element element = Element.of(reader.getName());
                    if (visited.contains(element)) {
                        throw unexpectedContent(reader);
                    }
                    visited.add(element);
                    switch (element) {
                        case DEPENDENCIES: parseDependencies(reader, specBuilder); break;
                        case MAIN_CLASS:   parseMainClass(reader, specBuilder); break;
                        case RESOURCES:    parseResources(factory, reader, specBuilder); break;
                        default: throw unexpectedContent(reader);
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static void parseDependencies(final XMLStreamReader reader, final ModuleSpec.Builder specBuilder) throws XMLStreamException {
        // xsd:choice
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    return;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    switch (Element.of(reader.getName())) {
                        case MODULE: parseModuleDependency(reader, specBuilder); break;
                        default: throw unexpectedContent(reader);
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static void parseModuleDependency(final XMLStreamReader reader, final ModuleSpec.Builder specBuilder) throws XMLStreamException {
        String group = null;
        String name = null;
        String version = null;
        boolean export = false;
        boolean optional = false;
        final Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.GROUP);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i ++) {
            final Attribute attribute = Attribute.of(reader.getAttributeName(i));
            required.remove(attribute);
            switch (attribute) {
                case GROUP:   group = reader.getAttributeValue(i); break;
                case NAME:    name = reader.getAttributeValue(i); break;
                case VERSION: version = reader.getAttributeValue(i); break;
                case EXPORT:  export = Boolean.parseBoolean(reader.getAttributeValue(i)); break;
                case OPTIONAL:optional = Boolean.parseBoolean(reader.getAttributeValue(i)); break;
                default: throw unexpectedContent(reader);
            }
        }
        if (! required.isEmpty()) {
            throw missingAttributes(reader.getLocation(), required);
        }
        final DependencySpec.Builder dependencySpecBuilder = specBuilder.addDependency(new ModuleIdentifier(group, name, version))
            .setExport(export)
            .setOptional(optional);

        // Process the nested <exports> element
        parseForExports(reader, dependencySpecBuilder);
    }

    private static void parseMainClass(final XMLStreamReader reader, final ModuleSpec.Builder specBuilder) throws XMLStreamException {
        String name = null;
        final Set<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i ++) {
            final Attribute attribute = Attribute.of(reader.getAttributeName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: name = reader.getAttributeValue(i); break;
                default: throw unexpectedContent(reader);
            }
        }
        if (! required.isEmpty()) {
            throw missingAttributes(reader.getLocation(), required);
        }
        specBuilder.setMainClass(name);
        // consume remainder of element
        parseNoContent(reader);
    }

    private static void parseResources(final ResourceLoaderFactory factory, final XMLStreamReader reader, final ModuleSpec.Builder specBuilder) throws XMLStreamException {
        // xsd:choice
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    return;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    switch (Element.of(reader.getName())) {
                        case RESOURCE_ROOT: {
                            parseResourceRoot(factory, reader, specBuilder);
                            break;
                        }
                        default: throw unexpectedContent(reader);
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static void parseResourceRoot(final ResourceLoaderFactory factory, final XMLStreamReader reader, final ModuleSpec.Builder specBuilder) throws XMLStreamException {
        String name = null;
        String path = null;
        final Set<Attribute> required = EnumSet.of(Attribute.PATH);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i ++) {
            final Attribute attribute = Attribute.of(reader.getAttributeName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: name = reader.getAttributeValue(i); break;
                case PATH: path = reader.getAttributeValue(i); break;
                default: throw unexpectedContent(reader);
            }
        }
        if (! required.isEmpty()) {
            throw missingAttributes(reader.getLocation(), required);
        }
        if (name == null) name = path;
        
        ResourceLoader resourceLoader;
        try
        {
           resourceLoader = factory.getResourceLoader(path, name);
        }
        catch (IOException e)
        {
           throw new XMLStreamException("Invalid JAR file specified", reader.getLocation(), e);
        }
        
        specBuilder.addRoot(name, resourceLoader);

        // Process the nested <exports> element
        parseForExports(reader, resourceLoader);
    }

    @SuppressWarnings("rawtypes")
    private static void parseForExports(final XMLStreamReader reader, final ExportFilterable filterable) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    return;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    switch (Element.of(reader.getName())) {
                        case EXPORTS: parseExports(reader, filterable); break;
                        default: throw unexpectedContent(reader);
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void parseExports(final XMLStreamReader reader, final ExportFilterable filterable) throws XMLStreamException {
        // xsd:choice
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    return;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    switch (Element.of(reader.getName())) {
                        case INCLUDE: parseInclude(reader, filterable); break;
                        case EXCLUDE: parseExclude(reader, filterable); break;
                        default: throw unexpectedContent(reader);
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    @SuppressWarnings("rawtypes")
    private static void parseInclude(final XMLStreamReader reader, final ExportFilterable filterable) throws XMLStreamException {
        String path = null;
        final Set<Attribute> required = EnumSet.of(Attribute.PATH);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i ++) {
            final Attribute attribute = Attribute.of(reader.getAttributeName(i));
            required.remove(attribute);
            switch (attribute) {
                case PATH: path = reader.getAttributeValue(i); break;
                default: throw unexpectedContent(reader);
            }
        }
        if (! required.isEmpty()) {
            throw missingAttributes(reader.getLocation(), required);
        }

        filterable.addExportInclude(path);

        // consume remainder of element
        parseNoContent(reader);
    }

    @SuppressWarnings("rawtypes")
    private static void parseExclude(final XMLStreamReader reader, final ExportFilterable filterable) throws XMLStreamException {
        String path = null;
        final Set<Attribute> required = EnumSet.of(Attribute.PATH);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i ++) {
            final Attribute attribute = Attribute.of(reader.getAttributeName(i));
            required.remove(attribute);
            switch (attribute) {
                case PATH: path = reader.getAttributeValue(i); break;
                default: throw unexpectedContent(reader);
            }
        }
        if (! required.isEmpty()) {
            throw missingAttributes(reader.getLocation(), required);
        }

        filterable.addExportExclude(path);

        // consume remainder of element
        parseNoContent(reader);
    }

    private static void parseNoContent(final XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    return;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw endOfDocument(reader.getLocation());
    }

    private static void parseEndDocument(final XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.END_DOCUMENT: {
                    return;
                }
                case XMLStreamConstants.CHARACTERS: {
                    if (! reader.isWhiteSpace()) {
                        throw unexpectedContent(reader);
                    }
                    // ignore
                    break;
                }
                case XMLStreamConstants.COMMENT:
                case XMLStreamConstants.SPACE: {
                    // ignore
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        return;
    }
}
