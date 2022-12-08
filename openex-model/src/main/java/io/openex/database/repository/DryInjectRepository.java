package io.openex.database.repository;

import io.openex.database.model.DryInject;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.NotNull;
import java.util.Optional;

@Repository
public interface DryInjectRepository extends CrudRepository<DryInject, String>, JpaSpecificationExecutor<DryInject> {

    @NotNull
    Optional<DryInject> findById(@NotNull String id);
}
