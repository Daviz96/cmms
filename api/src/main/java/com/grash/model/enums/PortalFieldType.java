package com.grash.model.enums;

public enum PortalFieldType {
    ASSET,
    LOCATION,
    DESCRIPTION,
    CONTACT,
    IMAGE,
    FILES,
    // Self-hosted: not a visible form field. Its presence on a portal switches the title
    // input to the "workstation / machine / other" label; absent means the classic
    // "request title" label. In both modes the submitted value goes to Request.title,
    // so nothing changes for the request that reaches the platform.
    STATION
}
