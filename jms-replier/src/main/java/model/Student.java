package model;

import java.util.Objects;

public class Student {

    private String userName;
    private String password;

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return userName;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public Student() { }

    public Student(String userName, String password) {
        this.userName = userName;
        this.password = password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Student student = (Student) o;
        return Objects.equals(userName, student.userName) &&
                Objects.equals(password, student.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName, password);
    }
}