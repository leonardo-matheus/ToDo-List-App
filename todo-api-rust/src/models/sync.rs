use serde::{Deserialize, Serialize};
use super::{ListResponse, TaskResponse};

#[derive(Debug, Deserialize)]
pub struct SyncPushRequest {
    #[serde(default)]
    pub lists: Vec<SyncListItem>,
    #[serde(default)]
    pub tasks: Vec<SyncTaskItem>,
    #[serde(default)]
    pub deleted_lists: Vec<String>,
    #[serde(default)]
    pub deleted_tasks: Vec<String>,
}

#[derive(Debug, Deserialize)]
pub struct SyncListItem {
    pub id: Option<String>,
    pub name: String,
    pub color: Option<String>,
    pub created_at: Option<String>,
    pub updated_at: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct SyncTaskItem {
    pub id: Option<String>,
    pub list_id: String,
    pub title: String,
    pub description: Option<String>,
    pub completed: Option<bool>,
    pub reminder: Option<String>,
    pub created_at: Option<String>,
    pub updated_at: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct SyncPushResponse {
    pub synced_lists: usize,
    pub synced_tasks: usize,
    pub deleted_lists: usize,
    pub deleted_tasks: usize,
    pub server_time: String,
}

#[derive(Debug, Deserialize)]
pub struct SyncPullRequest {
    pub last_sync: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct SyncPullResponse {
    pub lists: Vec<ListResponse>,
    pub tasks: Vec<TaskResponse>,
    pub deleted_lists: Vec<String>,
    pub deleted_tasks: Vec<String>,
    pub server_time: String,
}

#[derive(Debug, Serialize)]
pub struct SyncFullResponse {
    pub lists: Vec<ListResponse>,
    pub tasks: Vec<TaskResponse>,
    pub server_time: String,
}
