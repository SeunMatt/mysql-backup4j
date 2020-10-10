package com.smattme;

import java.util.List;

public class TablesResponse {

    private List<String> tables;
    private List<String> views;

    public TablesResponse() { }

    public TablesResponse(List<String> tables, List<String> views) {
        this.tables = tables;
        this.views = views;
    }

    public List<String> getTables() {
        return tables;
    }

    public List<String> getViews() {
        return views;
    }
}
