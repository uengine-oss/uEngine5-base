package org.uengine.social.repository;


import org.metaworks.multitenancy.persistence.MultitenantRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.uengine.social.entity.WorklistEntity;

import java.util.List;

/**
 * Created by uengine on 2017. 6. 19..
 */
@RepositoryRestResource(collectionResourceRel = "worklist", path = "worklist")
public interface WorklistRepository extends MultitenantRepository<WorklistEntity, Long> {

    @Query("select wl from WorklistEntity wl where wl.endpoint = ?#{loggedUserId} and wl.status = 'NEW'")
    public List<WorklistEntity> findToDo();
}

