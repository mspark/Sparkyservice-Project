package net.ssehub.sparkyservice.api.storeduser;


import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import net.ssehub.sparkyservice.api.testconf.UnitTestDataConfiguration;
import net.ssehub.sparkyservice.db.user.StoredUser;

/**
 * Test class for storing information into a database with an in-memory database with {@link StoredUserService}.
 * The logic checks will be done in {@link IStoredUserServiceTests} where the repositories are mocked and will 
 * return correct objects. 
 * 
 * @author Marcel
 */
@ExtendWith(SpringExtension.class)
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes= {UnitTestDataConfiguration.class})
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
public class StoredUserServiceDatabaseTests {
    
    @Autowired
    private StoredUserService userService;
    
    private static final String TEST_USER_NAME = "eatk234";
    
    @BeforeEach
    public void _storeUserToDB() {
        var user = new StoredUserDetails();
        user.setActive(true);
        user.setRealm(StoredUserDetails.DEFAULT_REALM);
        user.setUserName(TEST_USER_NAME);
        user.setRole(UserRole.DEFAULT);
        userService.storeUser(user);
    }

    /**
     * Positive test for storing a {@link StoredUserDetails} into the database. 
     * 
     * @throws UserNotFoundException if user is not found in database
     */
    @Test
    public void storeUserDetailsTest() throws UserNotFoundException {
        StoredUser loadedUser = userService.findUserById(1);
        assertAll(
                () -> assertEquals(TEST_USER_NAME, loadedUser.getUserName()),
                () -> assertEquals(StoredUserDetails.DEFAULT_REALM, loadedUser.getRealm()),
                () -> assertEquals(UserRole.DEFAULT.name(), loadedUser.getRole()),
                () -> assertTrue(loadedUser.isActive())
            );
    }

    @Test
    public void storedUserDetailsInstanceTest() throws UserNotFoundException {
        var storedUser = userService.findUserByNameAndRealm(TEST_USER_NAME, StoredUserDetails.DEFAULT_REALM);
        assertTrue(storedUser instanceof StoredUserDetails, "Users in the local realm should be an instance "
                + "of " + StoredUserDetails.class.getName());
    }

    /**
     * Positive test for finding a user by name and realm. 
     * 
     * @throws UserNotFoundException if user is not found in database
     */
    @Test
    public void findUserTest() throws UserNotFoundException {
        StoredUser loadedUser = userService.findUserByNameAndRealm(TEST_USER_NAME, StoredUserDetails.DEFAULT_REALM);
        assertNotNull(loadedUser, "User was not loaded from database.");
    }
    
    @Test
    public void changeRoleValueAndStoreTest() throws UserNotFoundException {
        StoredUser loadedUser = userService.findUserByNameAndRealm(TEST_USER_NAME, StoredUserDetails.DEFAULT_REALM);
        loadedUser.setRole(UserRole.ADMIN);
        userService.storeUser(loadedUser);
        loadedUser = userService.findUserByNameAndRealm(TEST_USER_NAME, StoredUserDetails.DEFAULT_REALM);
        assertEquals(UserRole.ADMIN.name(), loadedUser.getRole(), "The role was not changed inside the datbase.");
    }
    
    @Test
    public void dataDuplicateUserTest() {
        var secondUser = new StoredUserDetails();
        secondUser.setUserName(TEST_USER_NAME);
        secondUser.setRealm(StoredUserDetails.DEFAULT_REALM);
        assertThrows(DataIntegrityViolationException.class, () -> userService.storeUser(secondUser));
    }
    
    /**
     * Test if the application function is guaranteed when searching for null 
     * 
     * @throws UserNotFoundException
     */
    @Test
    public void findUserNullTest() throws UserNotFoundException {
        assertThrows(UserNotFoundException.class, () -> userService.findUserByNameAndRealm(null, null), "Null is not"
                + " supported as input while finding users."); 
    }
    
    @Test
    public void findMultipleEntries() throws UserNotFoundException {
        var user = new StoredUserDetails();
        user.setActive(true);
        user.setRealm("otherRealm");
        user.setUserName(TEST_USER_NAME);
        userService.storeUser(user);
        var users = userService.findUsersByUsername(TEST_USER_NAME);
        assertEquals(2, users.size());
    }

    @Test
    public void loadByUserNameInstanceTest() {
        assertTrue(userService.loadUserByUsername(TEST_USER_NAME) instanceof StoredUserDetails);
    }
}
