package org.apache.felix.framework.resolver;

import java.util.List;

import org.apache.felix.framework.capabilityset.Directive;
import org.apache.felix.framework.capabilityset.Requirement;
import org.apache.felix.framework.capabilityset.SimpleFilter;

public class FragmentRequirement implements Requirement
{
     private final Module m_owner;
     private final Requirement m_fragmentReq;

     public FragmentRequirement(Module owner, Requirement fragmentReq)
     {
         m_owner = owner;
         m_fragmentReq = fragmentReq;
     }

     public Requirement getRequirement()
     {
        return m_fragmentReq;
     }

     public Module getFragment()
     {
         return m_fragmentReq.getModule();
     }

     public Module getModule()
     {
         return m_owner;
     }

     public String getNamespace()
     {
         return m_fragmentReq.getNamespace();
     }

     public SimpleFilter getFilter()
     {
         return m_fragmentReq.getFilter();
     }

     public boolean isOptional()
     {
         return m_fragmentReq.isOptional();
     }

     public Directive getDirective(String name)
     {
         return m_fragmentReq.getDirective(name);
     }

     public List<Directive> getDirectives()
     {
         return m_fragmentReq.getDirectives();
     }

     public String toString()
     {
         return m_fragmentReq.toString();
     }
 }