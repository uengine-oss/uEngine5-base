package org.uengine.five;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.uengine.kernel.DefaultProcessInstance;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.social.ProcessInstanceEntity;
import org.uengine.social.ProcessInstanceRepository;

import java.util.Map;

/**
 * Created by uengine on 2017. 8. 9..
 */
@Component
@Scope("prototype")
public class JPAProcessInstance extends DefaultProcessInstance {

    ProcessInstanceEntity processInstanceEntity;
        public ProcessInstanceEntity getProcessInstanceEntity() {
            return processInstanceEntity;
        }
        public void setProcessInstanceEntity(ProcessInstanceEntity processInstanceEntity) {
            this.processInstanceEntity = processInstanceEntity;
        }

    @Autowired
    ProcessInstanceRepository processInstanceRepository;

    public JPAProcessInstance(ProcessDefinition procDefinition, String instanceId, Map options) throws Exception {
        super(procDefinition, instanceId, options);

        setProcessInstanceEntity(new ProcessInstanceEntity());
        getProcessInstanceEntity().setName(instanceId);

        processInstanceRepository.save(getProcessInstanceEntity());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void beforeCommit(){



    }


}