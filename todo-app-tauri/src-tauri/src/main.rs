// Previne janela de console no Windows em modo release
#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

fn main() {
    todo_app_lib::run();
}
