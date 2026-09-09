package com.shivankkapoor.aldrop.Repository;

import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

import com.shivankkapoor.aldrop.Data.AuthEvent;

public interface AuthEventRepository extends CrudRepository<AuthEvent, UUID> {

}
