package com.bidv.coretraining.admin.service;


public interface AdminOptionsService {
    com.bidv.coretraining.admin.dto.AdminOptionsDto options(String page);

    /**
     * Only the requested lists ("units", "domains", "programs", "courses"); null or empty means all of them.
     * A page that needs units only must not pay for reading every course and program.
     */
    com.bidv.coretraining.admin.dto.AdminOptionsDto options(String page, java.util.Set<String> parts);
}
