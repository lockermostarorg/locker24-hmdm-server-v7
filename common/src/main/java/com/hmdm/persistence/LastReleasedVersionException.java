package com.hmdm.persistence;

public class LastReleasedVersionException extends RuntimeException {

    public LastReleasedVersionException(Integer applicationVersionId) {
        super(String.format("Application version %s is the last released version of its application", applicationVersionId));
    }
}
