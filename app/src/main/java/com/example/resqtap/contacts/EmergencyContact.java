package com.example.resqtap.contacts;


/**
 * EmergencyContact
 * Data model untuk Emergency Contact (nama, phone number, relationship).
 */
public final class EmergencyContact {
    public final String id;
    public final String name;
    public final String relationship;
    public final String phone;
    public final String notes;

    public EmergencyContact(String id, String name, String relationship, String phone) {
        this(id, name, relationship, phone, "");
    }

    public EmergencyContact(String id, String name, String relationship, String phone, String notes) {
        this.id = id == null ? "" : id.trim();
        this.name = name == null ? "" : name.trim();
        this.relationship = relationship == null ? "" : relationship.trim();
        this.phone = phone == null ? "" : phone.trim();
        this.notes = notes == null ? "" : notes.trim();
    }
}
