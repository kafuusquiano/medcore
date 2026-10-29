package com.medcore.auth.mapper;

import com.medcore.auth.domain.entity.User;
import com.medcore.auth.domain.dto.response.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Conversion de {@link User} a su vista publica.
 *
 * <p>El mapeo es explicito y no automatico: una entidad nueva con un campo
 * sensible no debe filtrarse al DTO por el solo hecho de llamarse igual.
 * Por eso passwordHash, intentosFallidos y version no aparecen aqui.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "rol", source = "role.nombre")
    UserResponse aRespuesta(User usuario);
}
