package net.ssehub.sparkyservice.api.user.extraction;

import static net.ssehub.sparkyservice.api.util.NullHelpers.notNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import net.ssehub.sparkyservice.api.auth.SparkysAuthPrincipal;
import net.ssehub.sparkyservice.api.jpa.user.Password;
import net.ssehub.sparkyservice.api.jpa.user.UserRealm;
import net.ssehub.sparkyservice.api.jpa.user.UserRole;
import net.ssehub.sparkyservice.api.user.SparkyUser;
import net.ssehub.sparkyservice.api.user.creation.UserFactoryProvider;
import net.ssehub.sparkyservice.api.user.dto.UserDto;
import net.ssehub.sparkyservice.api.user.storage.UserNotFoundException;
import net.ssehub.sparkyservice.api.user.storage.UserStorageService;

/**
 * Simple user transformer which does the necessary work. Tries to make a compromise between performance
 * and simplicity.
 * 
 * @author marcel
 */
@Service
public class SimpleExtractionImpl implements UserExtractionService {

    @Autowired
    private UserStorageService storageService;

    /**
     * Get a role from a GrantedAuthority. Only a single authority is used (the first one). 
     * 
     * @param authorities
     * @return UserRole which was casted from an authority
     */
    @Nonnull
    private static UserRole getRole(Collection<? extends GrantedAuthority> authorities) {
        Object[] objList = authorities.toArray();
        String[] authList = Arrays.stream(objList).map(String::valueOf).toArray(String[]::new);
        return UserRole.DEFAULT.getEnum(authList[0]);
    }

    @Override
    @Nonnull
    public SparkyUser extractAndRefresh(@Nullable UserDetails details) throws UserNotFoundException {
        SparkyUser user;
        if (details == null) {
            throw new UserNotFoundException("Cant find null user");
        } else if (details instanceof SparkyUser) {
            user = (SparkyUser) details;
        } else if (details instanceof User) { // mocked Spring user - always memory
            var springUser = (User) details;
            user = notNull(transfromSpringUser(springUser).orElseThrow());
        } else {
            throw new UnsupportedOperationException("Unkown user type");
        }
        return user;
    }

    private static Optional<SparkyUser> transfromSpringUser(User details) {
        Optional<SparkyUser> optUser = Optional.empty();
        String passwordString = details.getPassword();
        if (passwordString != null) {
            var password = new Password(passwordString, "UNKWN");
            optUser = Optional.of(
                UserFactoryProvider.getFactory(UserRealm.MEMORY)
                    .create(details.getUsername(), password, getRole(details.getAuthorities()) , details.isEnabled())
            );
        }
        return optUser;
    }

    @Override
    @Nonnull
    public SparkyUser extendAndRefresh(@Nullable SparkysAuthPrincipal principal) throws UserNotFoundException {
        if (principal == null) {
            throw new UserNotFoundException("Principal was null");
        }
        return storageService.findUserByNameAndRealm(principal.getName(), principal.getRealm());
    }

    @Override
    @Nonnull
    public SparkyUser extractAndRefresh(@Nullable Authentication auth) throws MissingDataException {
        Object principal = Optional.ofNullable(auth).map(a -> a.getPrincipal()).orElseThrow(MissingDataException::new);
        return notNull(
            fromUserDetails(principal)
                .or(() -> fromSparkyPrincipal(principal))
                .or(() -> Optional.of(extract(auth)))
                .orElseThrow(() -> new UnsupportedOperationException("Not supported to extend from this auth"))
        );
    }

    private Optional<SparkyUser> fromUserDetails(Object obj) {
        Optional<SparkyUser> user; 
        try {
            user = Optional.ofNullable(obj)
                .filter(p -> UserDetails.class.isAssignableFrom(p.getClass()))
                .map(UserDetails.class::cast)
                .map(this::extractAndRefresh);
        } catch (UserNotFoundException e) {
            user = Optional.empty();
        }
        return user;
    }

    private Optional<SparkyUser> fromSparkyPrincipal(Object obj) {
        Optional<SparkyUser> user;
        try {
            return Optional.of(obj)
                .filter(p -> SparkysAuthPrincipal.class.isAssignableFrom(p.getClass()))
                .map(SparkysAuthPrincipal.class::cast)
                .map(this::extendAndRefresh);
        } catch (UserNotFoundException e) {
            user = Optional.empty();
        }
        return user;
    }

    @Override
    @Nonnull
    public SparkyUser extractAndRefresh(@Nullable UserDto user) throws MissingDataException {
        if (user == null) {
            throw new MissingDataException("UserDto was null");
        }
        return storageService.findUserByNameAndRealm(user.username, user.realm);
    }

    /**
     * Creates a SparkyUser without looking in the database for additional information. Is not suitable 
     * for editing purposes. Get a fresh copy from a storage in that case.
     */
    @Override
    @Nonnull
    public SparkyUser extract(@Nullable Authentication auth) {
        try {
            Object principal = Optional.ofNullable(auth).map(a -> a.getPrincipal()).orElseThrow();
            return notNull(
                fromUserDetails(principal)
                    .or(() -> tryExtractInformation(notNull(auth)))
                    .orElseThrow()
            );
        } catch (NoSuchElementException e) {
            throw new MissingDataException("Not enough information to extract from");
        }
    }

    /**
     * Tries to extract information of the authentication object an build a user. 
     * 
     * @param auth
     * @return User with information present from the authentication object
     * @throws MissingDataException When the principal is not an {@link SparkysAuthPrincipal}
     */
    private Optional<SparkyUser> tryExtractInformation(@Nonnull Authentication auth) {
        String username = auth.getName();
        Password pw = extractPassword(auth);
        return Optional.of(auth.getPrincipal())
                .filter(p -> SparkysAuthPrincipal.class.isAssignableFrom(p.getClass()))
                .map(SparkysAuthPrincipal.class::cast)
                .map(sp -> sp.getRealm())
                .map(UserFactoryProvider::getFactory)
                .map(factory -> factory.create(username, pw, getRole(auth.getAuthorities()), false));
    }

    @Nullable
    private Password extractPassword(@Nonnull Authentication auth) {
        Password pw = null;
        final var localCred = auth.getCredentials();
        if (localCred != null) {
            pw = new Password(notNull(localCred.toString()), "UNKWN");
        }
        return pw;
    }
}
