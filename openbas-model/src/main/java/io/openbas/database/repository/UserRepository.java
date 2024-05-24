package io.openbas.database.repository;

import io.openbas.database.model.User;
import io.openbas.database.raw.RawUser;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends CrudRepository<User, String>, JpaSpecificationExecutor<User>,
    StatisticRepository {

  @NotNull
  Optional<User> findById(@NotNull String id);

  Optional<User> findByEmailIgnoreCase(String email);

  List<User> findAllByEmailInIgnoreCase(List<String> emails);

  @Query("select user from User user where user.organization is null or user.organization.id in :organizationIds")
  List<User> usersAccessibleFromOrganizations(@Param("organizationIds") List<String> organizationIds);

  @Override
  @Query("select count(distinct u) from User u " +
      "join u.teams as team " +
      "join team.exercises as e " +
      "join e.grants as grant " +
      "join grant.group.users as user " +
      "where user.id = :userId and u.createdAt < :creationDate")
  long userCount(String userId, Instant creationDate);

  @Override
  @Query("select count(distinct u) from User u where u.createdAt < :creationDate")
  long globalCount(Instant creationDate);

  // -- ADMIN --

  // Custom query to bypass ID generator on User property
  @Modifying
  @Query(value = "insert into users(user_id, user_firstname, user_lastname, user_email, user_password, user_admin, user_status) "
      + "values (:id, :firstname, :lastName, :email, :password, true, 1)", nativeQuery = true)
  void createAdmin(
      @Param("id") String userId,
      @Param("firstname") String userFirstName,
      @Param("lastName") String userLastName,
      @Param("email") String userEmail,
      @Param("password") String userPassword
  );

  @Query(value = "select us.*, "
      + "       array_remove(array_agg(tg.tag_id), NULL) as user_tags,"
      + "       array_remove(array_agg(grp.group_id), NULL) as user_groups,"
      + "       array_remove(array_agg(tm.team_id), NULL) as user_teams from USERS us"
      + "       left join users_groups usr_grp on us.user_id = usr_grp.user_id"
      + "       left join groups grp on usr_grp.group_id = grp.group_id"
      + "       left join users_teams usr_tm on us.user_id = usr_tm.user_id"
      + "       left join teams tm on usr_tm.team_id = tm.team_id"
      + "       left join users_tags usr_tg on us.user_id = usr_tg.user_id"
      + "       left join tags tg on usr_tg.tag_id = tg.tag_id"
      + "      group by us.user_id;", nativeQuery = true)
  List<RawUser> rawAll();
}
