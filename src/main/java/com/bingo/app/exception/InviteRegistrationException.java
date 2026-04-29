package com.bingo.app.exception;

public class InviteRegistrationException extends RuntimeException {

    private final String userMessage;

    private InviteRegistrationException(String message, String userMessage) {
        super(message);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public static InviteRegistrationException invalidCode() {
        return new InviteRegistrationException(
                "Invite code does not exist",
                "This invite link is invalid. Ask your admin for a new link."
        );
    }

    public static InviteRegistrationException inactiveCode() {
        return new InviteRegistrationException(
                "Invite code is inactive",
                "This invite link has already been used or was deactivated."
        );
    }

    public static InviteRegistrationException inviterNotFound() {
        return new InviteRegistrationException(
                "Parent admin not found",
                "This invite link is no longer valid. Ask your admin for a new link."
        );
    }

    public static InviteRegistrationException invalidInviterRole() {
        return new InviteRegistrationException(
                "Inviter role is not allowed to create invite links",
                "This invite link is not valid for registration."
        );
    }

    public static InviteRegistrationException alreadyRegistered() {
        return new InviteRegistrationException(
                "User is already registered",
                "Your account is already registered. Use /start to open your menu."
        );
    }
}
