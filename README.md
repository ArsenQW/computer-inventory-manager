# Computer Inventory Manager

A Java command-line application for tracking computers and equipment. This is a local portfolio practice project, not an enterprise asset management system.

## Features

- Add devices with a unique serial number, brand, model, and operating system.
- List and search devices by their details or assigned user.
- Assign a working device to a user or return it to inventory.
- Edit device details and mark condition as Good, Needs Repair, or Retired.
- Distinguish Available, Assigned, and Out of Service devices.
- Delete a device after confirmation.
- Display totals and automatically save changes to a local file.
- Reject blank required fields and duplicate serial numbers (case insensitive).

## Requirements and run instructions

Install a **JDK 17 or newer**. No Maven, Gradle, or external libraries are needed.

Open Terminal in this project folder:

```bash
java InventoryManager.java
```

This uses Java's source-file launcher; no separate compilation step is needed. On Mac, check `java -version` first. If no Java installation is found, install a JDK 17 or newer and reopen Terminal.

In IntelliJ IDEA: open the folder as a Java project, choose a JDK 17 or newer, and run the `main` method in `InventoryManager.java`.

The default `devices.tsv` file is created in your current working directory. Run from the same folder each time. To choose a different data file:

```bash
java InventoryManager.java practice.tsv
```

## Quick demonstration

1. Choose 1 and enter `DEMO-1001`, `Example`, `Laptop 14`, `Linux`, and condition 1.
2. Choose 4, enter `DEMO-1001`, and assign it to `Alex Demo`.
3. Choose 3 and search for `linux`.
4. Exit with 0 and reopen to verify the saved device.
5. Choose 4 and leave the user blank to return the device.
6. Choose 5 to edit its details and condition.
7. Choose 6 and type `DELETE` to confirm removal.

See [DEMO.txt](DEMO.txt) for a recorded run.

## Tests

```bash
java InventoryManager.java --self-test
```

The built-in test uses a temporary folder and checks duplicate rejection, validation, search, assignments, edits, deletion, special-character round trips, persistence, and rejection of corrupt files without overwriting them.

## Code and storage design

- `Device`: an object representing one device and its availability.
- `Inventory`: a `LinkedHashMap` indexed by normalized serial numbers, plus file operations.
- Menu methods: input, display, and commands.

The file starts with `inventory-v1`. Every later line has six tab-separated Base64-encoded UTF-8 fields: serial, brand, model, operating system, assigned user, condition. Base64 handles special characters; it is **not encryption**. Save writes a temporary file before replacing the old file. Avoid editing the data file manually.

## Limitations

Single local user; no authentication, network access, or assignment history. Repair or retired devices cannot receive a new assignment. A device marked for repair can retain its existing assigned user until returned. The summary counts assigned users and available working devices, so those counts do not necessarily add up to the total when equipment is out of service.

## Concepts to understand

Classes and objects, constructors, collections, loops, exceptions, file persistence, and input validation. Start by following the Add device menu option through the `Device` constructor and `Inventory.save()`.

## Possible improvements

Add device categories, purchase dates, CSV export, an assignment history, or a JavaFX interface.

## Development note

This starter version was generated with AI assistance. Anyone using it as a portfolio project should review, run, explain, and personalize the code, and describe their own contributions accurately.
