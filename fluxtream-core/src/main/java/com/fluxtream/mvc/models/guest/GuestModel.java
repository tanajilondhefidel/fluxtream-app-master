package com.fluxtream.mvc.models.guest;

import com.fluxtream.domain.Guest;

public class GuestModel {

	public String username;
	public String firstname, lastname;
    public String email;
    public String roles;
    public long id;

	public GuestModel(Guest guest) {
		this.username = guest.username;
		this.firstname = guest.firstname;
		this.lastname = guest.lastname;
        this.email = guest.email;
        this.roles = guest.getUserRoles().toString();
        this.id=guest.getId();
    }
	
}
