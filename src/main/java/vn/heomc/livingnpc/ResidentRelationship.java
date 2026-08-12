package vn.heomc.livingnpc;

record ResidentRelationship(String type, String name) {
    ResidentRelationship {
        type = type == null ? "" : type;
        name = name == null ? "" : name;
    }
}
