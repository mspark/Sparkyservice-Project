package net.ssehub.sparkyservice.api.storeduser;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import net.ssehub.sparkyservice.db.user.StoredUser;

/**
 * Business logic for {@link StoredUser} and {@link StoredUserDetails}. This class is also used for a 
 * {@link UserDetailsService} authentication method through spring. It must provide a method for 
 * 
 * @author Marcel
 */
public interface IStoredUserService extends UserDetailsService {

    /**
     * Make a user persistent (in any kind of data storage).
     * 
     * @param <T> A class which must extends from {@link StoredUser} (which holds the JPA definitions).
     * @param user Is saved in a persistence way
     */
    <T extends StoredUser> void storeUser(@Nonnull T user);

    /**
     * Searches a data storage for an explicit user identified by unique id. 
     * 
     * @param id unique identifier
     * @return StoredUser with the id
     * @throws UserNotFoundException If no user with the given id is found in data storage
     */
    @Nonnull StoredUser findUserById(int id) throws UserNotFoundException;

    /**
     * Searches a data storage for all users with a given username. 
     * 
     * @param username 
     * @return
     * @throws UserNotFoundException
     */
    @Nonnull List<StoredUser> findUsersByUsername(@Nullable String username) throws UserNotFoundException;

    
    @Nonnull StoredUser findUserByNameAndRealm(@Nullable String username, @Nullable String realm) throws UserNotFoundException;

    /**
     * Checks if the given user is already stored in the used data storage. This could used as an indicator if the 
     * user will be edited or a new one is created.
     * 
     * @param user The user to check
     * @return <code>true</code> if the user was already stored in the past, <code>false</code> otherwise
     */
    boolean isUserInDatabase(@Nullable StoredUser user);

    /**
     * Is used by SpringSecurity for getting user details with a given username. It returns a single UserDetails
     * without limiting the search to a specific realm. Through this, a specific realm is always preferred (typically
     * the realm which is used for local authentication).
     * 
     * @param username name to look for
     * @return userDetails Details loaded from a data storage which is identified by the given username
     * @throws When a the given username is not found in storage (spring will continue 
     * with the next configured {@link AuthenticationProvider})
     */
    @Override
    @Nonnull UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;

    /**
     * Converts a given {@link UserDetails} to {@link StoredUser}. The details must be a supported implementation 
     * (anything which extends from {@link StoredUser}).
     * 
     * @param details typically provided by spring security during authentication process
     * @return StoredUser object with the values from the user details
     */
    @Nonnull StoredUser convertUserDetailsToStoredUser(@Nullable UserDetails details);
}
