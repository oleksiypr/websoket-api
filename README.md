# websocket-api

__WebSocket server__

WebSocket server for a lobby that implements the protocol and 
functionality described under *Websockets API*.

## API

1. ### Authentication

    Client sends
    
    ```json
        {
            "$type": "login",
            "username": "user1234",
            "password": "password1234"
        }
    ```
    
    Server responds
    
    ```json
        { "$type": "login_failed" }
    ```
    
    or
    
    ```json
        {
            "$type": "login_successful",
            "user_type": "admin"
        }
    ```
    
    User types are `admin` or `user`.

2. ### Pinging the server

    The client can ping the server to check your connectivity. 
    Client does a ping, including the `sequence number` 
    (which allows to trace the exact ping duration).
    
    ```json
      {
        "$type": "ping",
        "seq": 1
      }
    ```
    
    server will respond:
    
    ```json
      {
        "$type": "pong",
        "seq": 1
      }
    ```
3. ### Subscribing to the list of tables

    Client request
    
    ```json
    { "$type": "subscribe_tables" }
    ```
    
    Server will respond with the list of tables, and update the client with `table_added`, 
    `table_removed` and `table_updated` messages whenever the status has changed.
    
    ```json    
    {
      "$type": "table_list",
      "tables": [
        {
            "id": 1,
            "name": "table -James Bond",
            "participants": 7
        }, 
        {
            "id": 2,
            "name": "table -Mission Impossible",
            "participants": 4
        }
      ]
    }
    ```
    
4. ### Unsurprising from the list of tables
    
    ```json
     {"$type": "unsubscribe_tables"}
    ```
    
    (No response from server is expected here)

5. ### Privileged commands

    Only `admins` are allowed to use these commands, otherwise the server must 
    respond with:
    
    ```json
     { "$type": "not_authorized" }
    ```

    **Add table to server**
    
    Client sends:
    
    ```json
        {
          "$type": "add_table",
          "after_id": 1,
          "table": {
            "name": "table -Foo Fighters",
            "participants": 4
          }
        }
    ```

    **Update table**
    
    Client sends:
    
    ```json
    {
      "$type": "update_table",
      "table": {
        "id": 3,
        "name": "table -Foo Fighters",
        "participants": 4
        }
    }
    ```
    
    **Remove table**
    
    ```json
        {
          "$type": "remove_table",
          "id": 3
        }
    ```
 
## Events

When user edits or removes a table client must do an optimistic UI update. However if 
server responds with failure the optimistic update should be reverted.

1. ### Possible failure event:

    ```json
        {
          "$type": "removal_failed",
          "id": 3
        }
    ```

    or
    
    ```json
        {
          "$type": "update_failed",
          "id": 3
        }
    ```

2. ### Possible success events:

    - New table added
        
        ```json
        {
          "$type": "table_added",
          "after_id": 1,
          "table": {
            "id": 3,
            "name": "table -Foo Fighters",
            "participants": 9
          }
        }
        ```  
    
        If `after_id` is `-1`, the table has to be added at the beginning;
    
    - Table has been closed/removed
        
        ```json
        {
          "$type": "table_removed",
          "id": 1
        }
        ```  
    
    - Table updated
    
        ```json
        {
          "$type": "table_updated",
          "table": {
            "id": 3,
            "name": "table -Foo Fighters",
            "participants": 9
          }
        }
        ```