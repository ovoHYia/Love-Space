package com.lovespace.api;

import com.lovespace.api.dto.ApiDtos.*;
import com.lovespace.service.*;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ProfileController {
    private final AccountService accounts;
    private final MediaStorageService storage;
    public ProfileController(AccountService accounts, MediaStorageService storage) {
        this.accounts = accounts; this.storage = storage;
    }
    @PutMapping("/profile")
    public UserView profile(Authentication auth, @Valid @RequestBody ProfileRequest request) {
        return accounts.updateProfile(auth, request);
    }
    @PutMapping("/space")
    public CoupleView space(Authentication auth, @Valid @RequestBody SpaceNameRequest request) {
        return accounts.updateSpaceName(auth, request);
    }
    @PutMapping("/profile/password")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void password(Authentication auth, @Valid @RequestBody PasswordChangeRequest request) {
        accounts.changePassword(auth, request);
    }
    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MediaView avatar(Authentication auth, @RequestPart("avatar") MultipartFile avatar) {
        return storage.updateAvatar(auth, avatar);
    }
    @PutMapping("/moods/today")
    public MoodView mood(Authentication auth, @Valid @RequestBody MoodRequest request) {
        return accounts.setTodayMood(auth, request);
    }
}
