package com.bidv.coretraining.service;


/** Business operations for AssetAccessService. */
public interface AssetAccessService {
    public void record(String path);

    public void attach(String path);

    public void read(String path);
}
