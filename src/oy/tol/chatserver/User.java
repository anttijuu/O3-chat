package oy.tol.chatserver;

class User {
    private String username;
    private String password;
    private String email;

    User(String name, String pw, String mail) {
        username = name;
        password = pw;
        email = mail;
    }

    public String getName() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }
}
