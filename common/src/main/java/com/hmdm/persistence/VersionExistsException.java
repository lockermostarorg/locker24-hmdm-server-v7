package com.hmdm.persistence;

public class VersionExistsException extends RuntimeException {

    public VersionExistsException(String pkg, String version) {
        super(String.format("Version %s of application %s already exists", version, pkg));
    }
}
