package com.example.simpleerpsystem.maintenance.entity.enums;

public enum ServiceType {
    INSPECTION,             // General check-up
    PART_REPLACEMENT,       // Replacing a specific part
    CLEANING,               // E.g., filter cleaning, AC unit cleaning
    CONSULTATION,           // Providing advice or assessment
    REPAIR_WATER_FILTER,    // Specific repair for water filters
    REPAIR_AC_UNIT,         // Specific repair for AC units
    INSTALLATION_WATER_FILTER, // New installation
    INSTALLATION_AC_UNIT,   // New installation
    ANNUAL_MAINTENANCE_CONTRACT, // AMC service
    OTHER                   // For services not covered above
}
