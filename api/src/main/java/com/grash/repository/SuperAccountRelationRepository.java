package com.grash.repository;

import com.grash.model.SuperAccountRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SuperAccountRelationRepository extends JpaRepository<SuperAccountRelation, Long> {
    SuperAccountRelation findBySuperUser_IdAndChildUser_Id(Long superUserId, Long childUserId);

    // Fetch the child companies of a super account in a single query (session-safe).
    // Avoids initializing the LAZY User.superAccountRelations collection on a detached @CurrentUser,
    // which throws LazyInitializationException outside a Hibernate session (Work Order search bug).
    @Query("select distinct r.childUser.company.id from SuperAccountRelation r where r.superUser.id = :superUserId")
    List<Long> findChildCompanyIdsBySuperUserId(@Param("superUserId") Long superUserId);
}
