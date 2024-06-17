package io.github.chitchat.server.auth;

import io.github.chitchat.common.storage.database.DbUtil;
import io.github.chitchat.common.storage.database.models.inner.PermissionType;
import io.github.chitchat.common.storage.database.models.inner.UserType;
import io.github.chitchat.server.database.dao.ServerUserDAO;
import io.github.chitchat.server.database.models.ServerUser;
import io.github.chitchat.server.database.models.ServerUserSession;
import io.github.chitchat.server.database.service.ServerUserService;
import io.github.chitchat.storage.local.LocalStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.sqlite.core.DB;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public class Authentication
{
    private static final Logger log = LogManager.getLogger(Authentication.class);
    private static Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 60000, 10);

    private final ServerUserService sus;

    public Authentication(ServerUserService sus)
    {
        this.sus = sus;
    }

    public @Nullable ServerUser getSignUp(@NotNull String username, @NotNull String useremail, @NotNull String password, @NotNull String rePassword)
    {
        if(!(password.matches(rePassword)))
        {
            log.trace("Password is not matching");
            return null;
        }

        if(sus.getByName(username) != null || sus.getByEmail(useremail) != null)
        {
            log.trace("User already exists");
            return null;
        }

        String springBouncyHash = encoder.encode(password);
        return new ServerUser(DbUtil.newId(), UserType.USER, EnumSet.of(PermissionType.DELETE_MESSAGE, PermissionType.EDIT_MESSAGE, PermissionType.SEND_MESSAGE), username, useremail, springBouncyHash, Instant.now());;
    }

    public @Nullable ServerUserSession getSignIn(@NotNull String username, @NotNull String useremail, @NotNull String password)
    {
        int failedAttempts = 0;
        final int MAX_ATTEMPTS = 3;
        final int DELAY_SECONDS = 10; // against dos
        boolean loginSuccess = false;



        do
        {


            if (validateCredentials_ByName(username, password) || validateCredentials_ByEmail(useremail, password))
            {
                loginSuccess = true;
            }
            else
            {
                failedAttempts++;

                if (failedAttempts >= MAX_ATTEMPTS) // checks amount of failed attempts
                {

                }
                else
                {
                    log.trace("The username or password is incorrect! Please try this again.");
                }
            }
        }
        while (!loginSuccess);

        var user = sus.getByName(username);

        if(user.isEmpty())
        {
            return null;
        }

        return new ServerUserSession(user.get().getId(), DbUtil.newId().toString(), Instant.now().plus(Duration.ofDays(1)));
    }



    private boolean validateCredentials_ByName(String username, String password)
    {
        Optional<ServerUser> user = sus.getByName(username);

        if (user.isEmpty())
        {
            log.trace("User by username: {} does not exist", username);
            return false;
        }

        return encoder.matches(password, user.get().getPassword());
    }

    private boolean validateCredentials_ByEmail(String email, String password)
    {
        Optional<ServerUser> user = sus.getByEmail(email);

        if (user.isEmpty())
        {
            log.trace("User by email: {} does not exist", email);
            return false;
        }

        return encoder.matches(password, user.get().getPassword());
    }
}