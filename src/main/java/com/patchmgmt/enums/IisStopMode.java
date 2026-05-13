package com.patchmgmt.enums;

/**
 * Controls what IIS components are stopped before patch deployment on Windows.
 */
public enum IisStopMode {
    /** Stop only IIS Application Pools (recommended — faster, lower impact) */
    APPPOOL,
    /** Stop entire IIS Websites */
    SITE,
    /** Stop both Websites and Application Pools */
    BOTH
}
