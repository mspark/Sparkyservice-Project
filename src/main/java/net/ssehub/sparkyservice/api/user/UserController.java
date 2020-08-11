package net.ssehub.sparkyservice.api.user;

import static net.ssehub.sparkyservice.api.util.NullHelpers.notNull;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.servlet.ServletContext;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.ssehub.sparkyservice.api.conf.ControllerPath;
import net.ssehub.sparkyservice.api.jpa.user.User;
import net.ssehub.sparkyservice.api.jpa.user.UserRealm;
import net.ssehub.sparkyservice.api.jpa.user.UserRole;
import net.ssehub.sparkyservice.api.user.dto.ErrorDto;
import net.ssehub.sparkyservice.api.user.dto.NewUserDto;
import net.ssehub.sparkyservice.api.user.dto.UserDto;
import net.ssehub.sparkyservice.api.user.exceptions.MissingDataException;
import net.ssehub.sparkyservice.api.user.exceptions.UserEditException;
import net.ssehub.sparkyservice.api.user.exceptions.UserNotFoundException;
import net.ssehub.sparkyservice.api.util.ErrorDtoBuilder;

/**
 * @author Marcel
 */
@RestController
@Tag(name = "user-controller", description = "Controller for user managment")
public class UserController {

    private Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private ServletContext servletContext;

    @Autowired
    private IUserService userService;

    @Autowired
    private UserTransformer transformer;

    @Operation(summary = "Adds a new local user", security = { @SecurityRequirement(name = "bearer-key") })
    @PutMapping(ControllerPath.USERS_PUT)
    @ResponseStatus(HttpStatus.CREATED)
    @Secured(UserRole.FullName.ADMIN)
    public UserDto addLocalUser(@RequestBody @NotNull @Valid NewUserDto newUserDto) throws UserEditException {
        final @Nonnull String username = notNull(newUserDto.username); // spring validation
        final @Nonnull String password = notNull(newUserDto.password); // spring validation
        final @Nonnull var role = notNull(Optional.ofNullable(newUserDto.role).orElse(UserRole.DEFAULT));
        final var newUser = LocalUserDetails.newLocalUser(username, password, role);
        if (!userService.isUserInDatabase(newUser)) {
            userService.storeUser(newUser);
            log.info("Created new user: {}@{}", newUser.getUsername(), newUser.getRealm());
            return newUser.asDto();
        } else {
            log.info("No user added: Duplicate entry");
            throw new UserEditException("Can't add user: Already existing");
        }
    }

    @Operation(summary = "Edits users (in any realm)", description = "Edit and return the new user", 
            security = { @SecurityRequirement(name = "bearer-key") })
    @PatchMapping(value = ControllerPath.USERS_PATCH, consumes = { "application/json" })
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses(value = { 
            @ApiResponse(responseCode = "200", description = "User edit was successful", 
                content = @Content(schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "403", description = "Current user is not authorized to edit this user", 
                content = @Content),
            @ApiResponse(responseCode = "401", description = "Current user is not authenticated", 
                content = @Content),
            @ApiResponse(responseCode = "404", description = "The edit target was not found", 
                content = @Content),
        })
    @Secured({ UserRole.FullName.DEFAULT, UserRole.FullName.ADMIN })
    public UserDto editUser(@RequestBody @NotNull @Nonnull @Valid UserDto userDto, @Nonnull Authentication auth)
            throws UserNotFoundException, MissingDataException {
        var authenticatedUser = notNull(Optional.ofNullable(transformer.extendFromAuthentication(auth)).orElseThrow(
            () -> new UserNotFoundException("The authenticated user can't be edited or the database is down")));
        boolean selfEdit = authenticatedUser.getUserName().equals(userDto.username)
                && authenticatedUser.getRealm().equals(userDto.realm);
        var authority = (GrantedAuthority) auth.getAuthorities().toArray()[0];
        User editTargetUser = null;
        if (UserRole.ADMIN.getEnum(authority.getAuthority()) == UserRole.ADMIN) {
            editTargetUser = userService.findUserByNameAndRealm(userDto.username, userDto.realm);
            User.adminUserDtoEdit(editTargetUser, userDto);
        } else if (selfEdit) {
            editTargetUser = userService.findUserByNameAndRealm(userDto.username, userDto.realm);
            User.defaultUserDtoEdit(editTargetUser, userDto);
        } else {
            log.info("User {}@{} tries to modify the data of other user without admin privileges",
                    authenticatedUser.getUserName(), authenticatedUser.getRealm());
            log.debug("Edit target was: {}@{}", userDto.username, userDto.realm);
            throw new AccessDeniedException("Not allowed to modify other users data");
        }
        userService.storeUser(editTargetUser);
        return editTargetUser.asDto();
    }

    @Operation(summary = "Gets all users from all realms", security = { @SecurityRequirement(name = "bearer-key") })
    @GetMapping(ControllerPath.USERS_GET_ALL)
    @Secured(UserRole.FullName.ADMIN)
    public UserDto[] getAllUsers() {
        var list = userService.findAllUsers();
        var dtoArray = new UserDto[list.size()];
        for (int i = 0; i < list.size(); i++) {
            dtoArray[i] = list.get(i).asDto();
        }
        return dtoArray;
    }

    @Operation(summary = "Gets all users from a single realm", security = { @SecurityRequirement(name = "bearer-key") })
    @GetMapping(ControllerPath.USERS_PREFIX + "/{realm}")
    @Secured(UserRole.FullName.ADMIN)
    public UserDto[] getAllUsersFromRealm(@PathVariable("realm") UserRealm realm) {
        var list = userService.findAllUsersInRealm(realm);
        var dtoArray = new UserDto[list.size()];
        for (int i = 0; i < list.size(); i++) {
            dtoArray[i] = list.get(i).asDto();
        }
        return dtoArray;
    }

    @Operation(summary = "Gets a unique user", security = { @SecurityRequirement(name = "bearer-key") })
    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Return user"),
            @ApiResponse(responseCode = "403", description = "User is not authorized"),
            @ApiResponse(responseCode = "401", description = "User is not authenticated "),
            @ApiResponse(responseCode = "404", description = "The desired user or realm was not found") })
    @GetMapping(ControllerPath.USERS_PREFIX + "/{realm}/{username}")
    public UserDto getSingleUser(@PathVariable("realm") UserRealm realm, @PathVariable("username") String username,
            Authentication auth) throws  MissingDataException {
        var singleAuthy = (GrantedAuthority) auth.getAuthorities().toArray()[0];
        var role = UserRole.DEFAULT.getEnum(singleAuthy.getAuthority());
        User authenticatedUser = userService.getDefaultTransformer().extendFromAuthentication(auth);
        if (username.equals(authenticatedUser.getUserName()) || role == UserRole.ADMIN) {
            var user = userService.findUserByNameAndRealm(username, realm);
            return user.asDto();
        } else {
            throw new AccessDeniedException("Modifying this user is not allowed..");
        }
    }

    @Operation(summary = "Deletes a user", security = { @SecurityRequirement(name = "bearer-key") })
    @DeleteMapping(ControllerPath.USERS_DELETE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Secured(UserRole.FullName.ADMIN)
    @ApiResponses(value = { @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "403", description = "User is not authorized"),
            @ApiResponse(responseCode = "401", description = "User is not authenticated "),
            @ApiResponse(responseCode = "404", description = "The desired user was not found") })
    public void deleteUser(@PathVariable("realm") UserRealm realm, @PathVariable("username") String username) {
        userService.deleteUser(username, realm);
    }

    /**
     * Exception handler for {@link UserController} for exceptions which occur during user edititation. 
     * @param 
     * @return ErrorDTO with all collected information about the error
     */
    @ResponseStatus(code = HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public ErrorDto handleAccessViolationException(AccessDeniedException ex) {
        return new ErrorDtoBuilder().newError(ex.getMessage(), HttpStatus.FORBIDDEN, servletContext.getContextPath())
                .build();
    }

    /**
     * Exception handler for {@link UserController} for exceptions which occur during user edititation. 
     * @param 
     * @return ErrorDTO with all collected information about the error
     */
    @ResponseStatus(code = HttpStatus.NOT_FOUND)
    @ExceptionHandler(value = UserNotFoundException.class)
    public ErrorDto handleUserNotFoundException(Exception e) {
        return new ErrorDtoBuilder().newError("User target not found", 
                HttpStatus.NOT_FOUND, servletContext.getContextPath()).build();
    }

    /**
     * Exception handler for {@link UserController} for exceptions which occur during user edititation. 
     * @param 
     * @return ErrorDTO with all collected information about the error
     */
    @ResponseStatus(code = HttpStatus.CONFLICT)
    @ExceptionHandler(UserEditException.class)
    public ErrorDto handleUserEditException(UserEditException ex) {
        return new ErrorDtoBuilder().newError(null, HttpStatus.CONFLICT, servletContext.getContextPath()).build();
    }

    /**
     * Exception handler for {@link UserController} for exceptions which occur during user edititation. 
     * @param 
     * @return ErrorDTO with all collected information about the error
     */
    @ResponseStatus(code = HttpStatus.BAD_REQUEST)
    @ExceptionHandler({Exception.class})
    public ErrorDto handleException(Exception ex) {
        log.info("Exception in user controller: {}", ex.getCause());
        log.debug("" +  ex.getStackTrace());
        return new ErrorDtoBuilder().newError(ex.getClass().getName(), HttpStatus.INTERNAL_SERVER_ERROR,
                servletContext.getContextPath()).build();
    }
}
