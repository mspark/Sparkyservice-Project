package net.ssehub.sparkyservice.api.storeduser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import net.ssehub.sparkyservice.db.user.StoredUser;

@Service
@ParametersAreNonnullByDefault
public class StoredUserService implements IStoredUserService {
 
    @Autowired
    private StoredUserRepository repository;
    
    public <T extends StoredUser> void storeUser(@Nonnull T user) {
        StoredUser stUser = new StoredUser((StoredUser) user);
        repository.save(stUser);
    }
        
    @Override
    public List<StoredUser> findUsersByUsername(String username) throws UserNotFoundException {
        Optional<List<StoredUser>> usersByName = repository.findByuserName(username);
        usersByName.orElseThrow(() -> new UserNotFoundException("No user with this name was found in database"));
        return usersByName.get();
    }
    
    @Override
    @Nonnull
    public StoredUser findUserByNameAndRealm(String username, String realm) throws UserNotFoundException {
        Optional<StoredUser> user = repository.findByuserNameAndRealm(username, realm);
        return user.orElseThrow(() -> new UserNotFoundException("no user with this name in the given realm"));
        //return user.orElseGet("");
    }
     
    @Override
    public StoredUser findUserByid(int id) throws UserNotFoundException {
        Optional<StoredUser> user = repository.findById(id);
        user.orElseThrow(() -> new UserNotFoundException("Id was not found in database"));
        return user.map(StoredUserDetails::new).get();
    }

    /**
     * Used by spring security for loading users (springs {@link UserDetails} service) from the local database.
     * Because this method is only used by spring for local user lookups, 
     * it will search only in {@link StoredUserDetails.DEDEFAULT_REALM}.
     * 
     * @param username name to look for
     * @return userDetails from the database - never null
     * @throws When a the given username is not found in the database with the default realm - spring will continue 
     * with the next configured AuthProvider
     */
    @Override
    @Nonnull
    public UserDetails loadUserByUsername(@Nullable String username) throws UsernameNotFoundException {
        try {
            if (username == null) {
                throw new UsernameNotFoundException("User with name \"null\" not found");
            } 
            final var storedUser = findUserByNameAndRealm(username, StoredUserDetails.DEFAULT_REALM);
            return new StoredUserDetails(storedUser);
        } catch (UserNotFoundException e) {
            throw new UsernameNotFoundException(e.getMessage());
        }
    }

    private static List<StoredUser> transformToCustomUser(List<StoredUser> users) {
        List<StoredUser> customUsersList = new ArrayList<StoredUser>();
        for(StoredUser singleUser : users) {
            customUsersList.add(new StoredUserDetails(singleUser));
        }
        return customUsersList;
    }

}
