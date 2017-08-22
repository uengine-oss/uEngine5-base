package org.uengine.social.service;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.modeling.resource.*;
import org.uengine.processpublisher.BPMNUtil;
import org.uengine.uml.model.ClassDefinition;
import org.uengine.util.UEngineUtil;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by uengine on 2017. 8. 9..
 *
 * Implementation Principals:
 *  - REST Maturity Level : 2
 *  - Not using old uEngine ProcessManagerBean, this replaces the ProcessManagerBean
 *  - ResourceManager and CachedResourceManager will be used for definition caching (Not to use the old DefinitionFactory)
 *  - json must be Typed JSON to enable object polymorphism - need to change the jackson engine. TODO: accept? typed json is sometimes hard to read
 */
@RestController
public class DefinitionService {

    final String resourceRoot = "codi";

    @Autowired
    ResourceManager resourceManager;



    @PostConstruct
    public void init(){
    }

    // ---------------- CRUD mappings -------------------- //

    @RequestMapping(value = "/definitions", method = RequestMethod.GET)
    public List<String> listDefinition() throws Exception {

        IContainer resource = new ContainerResource();
        resource.setPath(resourceRoot + "/");
        List<IResource> resources = resourceManager.listFiles(resource);

        List<String> resourcesList = new ArrayList<String>();

        for(IResource resource1 : resources){
            resourcesList.add(resource1.getPath().substring(resourceRoot.length()));
        }

        return resourcesList;  //TODO: Need to be changed to HATEOAS _self link instead
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.setVisibilityChecker(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));

        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); //ignore null
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT); //ignore zero and false when it is int or boolean

        objectMapper.enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, "_type");
        return objectMapper;
    }

    @RequestMapping(value = "/definition/{defPath:.+}", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public Object getDefinition(@PathVariable("defPath") String definitionPath) throws Exception {

        IResource resource = new DefaultResource(resourceRoot + "/" + definitionPath);
        Object object = resourceManager.getObject(resource);

        ObjectMapper objectMapper = createObjectMapper();
        DefinitionWrapper definitionWrapper = new DefinitionWrapper((Serializable) object);
        String uEngineProcessJSON = objectMapper.writeValueAsString(definitionWrapper);

        ///// Later we will use BPMN exporter: after 2017. 9.
//        TDefinitions tDefinitions = (TDefinitions) BPMNUtil.exportAdapt(object);
//
//        for(JAXBElement element : tDefinitions.getRootElement()) {
//            if (element.getValue() instanceof TProcess) {
//                TProcess bpmnProcess = (TProcess) element.getValue();
//
//                TExtensionElements tExtensionElements = new TExtensionElements();
//
//                org.uengine.bpmn.Field field = new org.uengine.bpmn.Field();
//                field.setName("object");
//                field.setStringValue("{a:'a'}");
//
//                tExtensionElements.getAny().add(field);
//                bpmnProcess.setExtensionElements(tExtensionElements);
//            }
//        }
//
//        org.omg.spec.bpmn._20100524.model.ObjectFactory objectFactory = new org.omg.spec.bpmn._20100524.model.ObjectFactory();
//
//        JAXBElement<TDefinitions> element = objectFactory.createDefinitions(tDefinitions);
//        JAXBContext jaxbContext = JAXBContext.newInstance(TDefinitions.class, org.uengine.bpmn.Field.class);
//
//        Marshaller marshaller = jaxbContext.createMarshaller();
//        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
//
//        ByteArrayOutputStream bao = new ByteArrayOutputStream();
//        marshaller.marshal(element, bao);
//        String bpmn = new String(bao.toByteArray(), "UTF-8");
//
//        //return bpmn;

        return uEngineProcessJSON;
    }

    /**
     * TODO: need ACL referenced by token
     * @param definitionPath
     * @param definition
     * @throws Exception
     */
    @RequestMapping(value = "/definition/{defPath:.+}", method = RequestMethod.POST)
    public void putDefinition(@PathVariable("defPath") String definitionPath, @RequestBody String definition) throws Exception {

        IResource resource = new DefaultResource(resourceRoot + "/" + definitionPath);

        if(definitionPath.endsWith(".process")) {

            //TODO [severe] BPMNUtil.importAdapt(InputStream) must be available. using temp file will arise a multi-thread problem.
            ByteArrayInputStream bai = new ByteArrayInputStream(definition.getBytes("UTF-8"));

            UEngineUtil.copyStream(bai, new FileOutputStream("test.bpmn"));
            ProcessDefinition processDefinition = BPMNUtil.importAdapt(new File("test.bpmn"));

            resourceManager.save(resource, processDefinition);
        }else if(definitionPath.endsWith(".class")){

            ObjectMapper mapper = new ObjectMapper();
            ClassDefinition classDefinition = mapper.readValue(definition, ClassDefinition.class);

            resourceManager.save(resource, classDefinition);
        }else if(definitionPath.endsWith(".json")){

            ObjectMapper objectMapper = createObjectMapper();
            DefinitionWrapper definitionWrapper = objectMapper.readValue(definition, DefinitionWrapper.class);

            if(definitionWrapper.getDefinition()==null) throw new Exception("Definition is corrupt.");

            resourceManager.save(resource, definitionWrapper.getDefinition());

        }else
            throw new Exception("unknown resource type: " + definitionPath);
    }

    @RequestMapping(value = "/definition/{defPath}", method = RequestMethod.DELETE)
    public void deleteDefinition(@PathVariable("defPath") String definitionPath) throws Exception {

        IResource resource = new DefaultResource(resourceRoot + "/" + definitionPath);
        resourceManager.delete(resource);
    }


    // ----------------- execution services -------------------- //

    @RequestMapping(value = "/definition/{defPath}/instance", method = RequestMethod.POST)
    public String runDefinition(@PathVariable("defPath") String definitionPath, @RequestBody String arguments) throws Exception {

        IResource resource = new DefaultResource(resourceRoot + "/" + definitionPath);
        Object definition = resourceManager.getObject(resource);

        if(definition instanceof ProcessDefinition){
            ProcessDefinition processDefinition = (ProcessDefinition) definition;
            { //TODO: will be moved to afterDeserialize of ProcessDefinition
                processDefinition.setId(resource.getPath());
                if(processDefinition.getName()==null)
                    processDefinition.setName(resource.getPath());
            }


            //org.uengine.kernel.ProcessInstance instance = processDefinition.createInstance();

            org.uengine.kernel.ProcessInstance instance = applicationContext.getBean(
                    org.uengine.kernel.ProcessInstance.class,
                    new Object[]{
                        processDefinition,
                            null,
                            null
                    }
            );

            instance.execute();

            return instance.getInstanceId(); //TODO: returns HATEOAS _self link instead.
        }

        return null;

    }

    @Autowired
    ApplicationContext applicationContext;


}