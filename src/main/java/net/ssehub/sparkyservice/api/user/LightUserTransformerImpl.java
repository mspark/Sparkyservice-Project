package net.ssehub.sparkyservice.api.user;

import static net.ssehub.sparkyservice.api.util.NullHelpers.notNull;

import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.userdetails.LdapUserDetails;

import net.ssehub.sparkyservice.api.auth.SparkysAuthPrincipal;
import net.ssehub.sparkyservice.api.jpa.user.Password;
import net.ssehub.sparkyservice.api.jpa.user.User;
import net.ssehub.sparkyservice.api.jpa.user.UserRealm;
import net.ssehub.sparkyservice.api.jpa.user.UserRole;
import net.ssehub.sparkyservice.api.user.dto.UserDto;
import net.ssehub.sparkyservice.api.user.exceptions.MissingDataException;
import net.ssehub.sparkyservice.api.user.exceptions.UserNotFoundException;
import net.ssehub.sparkyservice.api.util.NullHelpers;

public final class LightUserTransformerImpl implements UserTransformer {

    @Autowired
    private IUserService userSerivce;
    private final Logger log = LoggerFactory.getLogger(UserTransformer.class);

    /**
     * Should only be used for springs bean definition. Otherwise this could lead to
     * unexpected behavior.
     */
    public LightUserTransformerImpl() {
    }

    LightUserTransformerImpl(IUserService userService) {
        this.userSerivce = userService;
    }

    /**
     * {@inheritDoc}
     * 
     * This implementation tries to be lightweight as possible and get all necessary
     * information from the provided user details without doing database operations.
     * If too much information are missing, it tries to load them from the data
     * backend (database).
     */
    @Override
    public @Nonnull User extendFromUserDetails(@Nullable UserDetails details) throws UserNotFoundException {
        if (details != null && details.getUsername() != null && !details.getUsername().isEmpty()) {
            User storeUser;
            try {
                storeUser = castFromUserDetails(details);
            } catch (MissingDataException e) {
                if (details instanceof LdapUserDetails) {
                    storeUser = userSerivce.findUserByNameAndRealm(details.getUsername(), UserRealm.LDAP);
                }
                log.info("Can't search a specific realm. Search for any user with the given username to complete"
                        + " the request. This could lead to problems!");
                var user = userSerivce.findUsersByUsername(details.getUsername()).get(0);
                storeUser = notNull(user);
            }
            return storeUser;
        }
        throw new UserNotFoundException("User could not be casted. Username not found");
    }

    /**
     * Tries to cast a given instance of {@link UserDetails} to a {@link User}
     * object. This is done without database interactions and is more resource
     * friendly than {@link #extendFromUserDetails(UserDetails)} but may can't
     * complete the challenge if some really essential information are missing
     * (typically identifier).<br>
     * Supported implementations: <br>
     * <ul>
     * <li>{@link LocalUserDetails}</li>
     * <li>{@link LdapUserDetailsImpl}</li>
     * </ul>
     * Otherwise the method returns a {@link User} which may is incomplete and is in
     * an "UNKNOWN" realm.
     * 
     * @param details typically provided by spring security during authentication
     *                process
     * @return StoredUser which holds data from the given details
     * @throws Thrown if the provided information are too less in order to create an
     *                object and the user is not found in the database
     */
    public @Nonnull User castFromUserDetails(@Nullable UserDetails details) throws MissingDataException {
        User storeUser = null;
        if (details instanceof LdapUserDetails) {
            try {
                var role = getRoleFromAuthority(details.getAuthorities());
                String username = Optional.ofNullable(details.getUsername()).orElse("");
                storeUser = new User(notNull(username), null, UserRealm.LDAP, details.isEnabled(), role);
            } catch (UnsupportedOperationException e) {
                log.debug("Using incomplete information for ldap user.");
                throw new MissingDataException("Can't cast LDAP user from user details due to wrong roles");
            }
        } else if (details instanceof LocalUserDetails || details instanceof User) {
            storeUser = (User) details;
        } else if (details instanceof org.springframework.security.core.userdetails.User) {
            var springUser = (org.springframework.security.core.userdetails.User) details;
            var role = getRoleFromAuthority(details.getAuthorities());
            String username = Optional.ofNullable(details.getUsername()).orElse("");
            String password = Optional.ofNullable(springUser.getPassword()).orElse("");
            storeUser = new User(notNull(username), new Password(notNull(password)), UserRealm.MEMORY, details.isEnabled(),
                    role);
        } else if (details != null) {
            var role = getRoleFromAuthority(details.getAuthorities());
            String username = Optional.ofNullable(details.getUsername()).orElse("");
            storeUser = new User(notNull(username), null, UserRealm.UNKNOWN, details.isEnabled(), role);
        }
        var returnVal = Optional.ofNullable(storeUser).orElseThrow(
                () -> new MissingDataException("User cast was not " + "possible with the provided information"));
        return NullHelpers.notNull(returnVal);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nonnull User extendFromSparkyPrincipal(@Nullable SparkysAuthPrincipal principal)
            throws UserNotFoundException {
        // TODO maybe find ligthweight cast
        if (principal == null) {
            throw new UserNotFoundException("User with name null could not be found or casted");
        }
        return userSerivce.findUserByNameAndRealm(principal.getName(), principal.getRealm());
    }

    private @Nonnull UserRole getRoleFromAuthority(@Nullable Collection<? extends GrantedAuthority> authorities) {
        if (authorities != null && authorities.size() == 1) {
            for (var authority : authorities) {
                try {
                    return UserRole.DEFAULT.getEnum(authority.getAuthority());
                } catch (IllegalArgumentException e){
                    log.warn("Invalid role found:" + authorities.toString() + ". Using default role");
                }
            }
            return UserRole.DEFAULT;
        } else {
            throw new UnsupportedOperationException(
                    "Not supported: Can't parse user no role or with more than one role.");
        }
    }

    /**
     * {@inheritDoc}
     * 
     * Supported principal instances: <br>
     * <ul>
     * <li>{@link SparkysAuthPrincipal}</li>
     * <li>{@link UserDetails}</li>
     * </ul>
     */
    @Override
    public @Nullable User extendFromAny(@Nullable Object principal) throws MissingDataException {
        User user = null;
        if (principal instanceof SparkysAuthPrincipal) {
            user = extendFromSparkyPrincipal((SparkysAuthPrincipal) principal);
        } else if (principal instanceof UserDetails) {
            user = castFromUserDetails((UserDetails) principal);
        } else if (principal instanceof UserDto) {
            user = extendFromUserDto((UserDto) principal);
        }
        return user;
    }

    @Override
    public @Nonnull User extendFromUserDto(@Nullable UserDto userDto)
            throws MissingDataException, UserNotFoundException {
        if (userDto != null && userDto.username != null && userDto.realm != null) {
            // validate and try a cast
            // TODO implement a castMethod before doing a heavy database transaction.
            // StoredUser user = new StoredUser(userDto.username, null, userDto.realm,
            // false, UserRole.DEFAULT);
            return userSerivce.findUserByNameAndRealm(userDto.username, userDto.realm);
        }
        throw new MissingDataException("Identifier not provided");
    }
}
