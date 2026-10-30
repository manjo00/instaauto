# Graph Report - instaauto  (2026-10-30)

## Corpus Check
- 118 files · ~69,346 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 17 file(s) not represented in the graph (top: .xml 9, (none) 2, .properties 2)

## Summary
- 1681 nodes · 4316 edges · 78 communities (34 shown, 44 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 56 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `94135109`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- CaptionCoachTest
- Test
- Test
- MediaFileStore
- QueuePlannerTest
- PostType
- ComposePostScreen.kt
- ScheduleCalculatorTest
- HashtagPresetEntity
- QueueReorderTest.kt
- androidTest/java/com/autoinsta/data/media/MediaFileStoreTest.kt
- HomeScreen.kt
- PostWorkerTest
- ScheduleScreen.kt
- DoneList.kt
- AuthDtos.kt
- QueueRepository
- SlotAttempt
- ComposePostViewModel
- CaptionCoachScreen.kt
- SettingsScreen.kt
- ScheduledPostWithMedia
- ExactAlarmBanner.kt
- PostStatus
- HashtagSetTest
- QueueFormat.kt
- MediaType
- DragReorderTest
- MainActivity.kt
- PublishRepositoryTest.kt
- AppEventEntity
- MigrationTest
- PostingSlotEntity
- TokenLifecycleTest
- MediaItemEntity
- TokenRefreshWorker.kt
- InstagramApi
- QueueSettingsEntity
- TokenStore
- PostWorker.kt
- HomeViewModel
- SettingsViewModel.kt
- TokenStoreTest.kt
- PublishRepositoryTest
- AccountEntity
- AccountRepository
- Notifier.kt
- BootReceiver.kt
- AuthDtosTest
- PublishDtos.kt
- ScheduleViewModel
- BackgroundHealth.kt
- PostHistoryDao
- PostHistoryEntity
- PostRepository
- AppDatabase
- QueueRepository.kt
- CloudinaryUploader
- AutoInstaApp
- Result
- TokenLifecycle
- render_diagnostics.py
- Theme.kt
- .onCreate
- State
- PresetsScreen
- Factory

## God Nodes (most connected - your core abstractions)
1. `QueueRepository` - 39 edges
2. `PostType` - 38 edges
3. `AutoInstaApp` - 37 edges
4. `ScheduledPostWithMedia` - 37 edges
5. `MediaFitTest` - 37 edges
6. `MediaType` - 36 edges
7. `PostStatus` - 35 edges
8. `QueuePlannerTest` - 35 edges
9. `CaptionCoachTest` - 34 edges
10. `ComposePostViewModel` - 33 edges

## Surprising Connections (you probably didn't know these)
- `MediaThumbnail()` --calls--> `mediaModel()`  [INFERRED]
  app/src/main/java/com/autoinsta/ui/components/MediaThumbnail.kt → app/src/main/java/com/autoinsta/ui/components/MediaModel.kt
- `ComposePostScreen()` --calls--> `ComposePostViewModel`  [INFERRED]
  app/src/main/java/com/autoinsta/ui/composepost/ComposePostScreen.kt → app/src/main/java/com/autoinsta/ui/composepost/ComposePostViewModel.kt
- `ComposePostScreen()` --calls--> `MediaFitEditor()`  [INFERRED]
  app/src/main/java/com/autoinsta/ui/composepost/ComposePostScreen.kt → app/src/main/java/com/autoinsta/ui/composepost/MediaFitEditor.kt
- `HomeScreen()` --calls--> `DoneList()`  [INFERRED]
  app/src/main/java/com/autoinsta/ui/home/HomeScreen.kt → app/src/main/java/com/autoinsta/ui/home/DoneList.kt
- `AddSlotDialog()` --calls--> `shortDayName()`  [INFERRED]
  app/src/main/java/com/autoinsta/ui/queue/ScheduleScreen.kt → app/src/main/java/com/autoinsta/ui/queue/QueueFormat.kt

## Import Cycles
- None detected.

## Communities (78 total, 44 thin omitted)

### Community 0 - "CaptionCoachTest"
Cohesion: 0.05
Nodes (32): AnthropicApi, CaptionDto, CoachContentDto, CoachMessageDto, CoachPayloadDto, CoachRequestDto, CoachResponseDto, ImageSourceDto (+24 more)

### Community 1 - "Test"
Cohesion: 0.07
Nodes (20): FakePublisher, FileRef, PermanentFailure, PublishRepository, PublishResult, Success, TransientFailure, CaptionVerdict (+12 more)

### Community 2 - "Test"
Cohesion: 0.08
Nodes (15): Acceptable, CropWindow, MediaFit, Mode, AS_IS, CROP, PAD, TooTall (+7 more)

### Community 3 - "MediaFileStore"
Cohesion: 0.07
Nodes (6): MediaDurabilityTest, NoOpScheduler, Dimensions, MediaFileStore, MediaToSave, MediaFileStoreTest

### Community 4 - "QueuePlannerTest"
Cohesion: 0.13
Nodes (9): Assignment, Plan, PublishNow, QueuedAction, QueuePlanner, RollForward, Slot, WaitUntil (+1 more)

### Community 5 - "PostType"
Cohesion: 0.09
Nodes (15): PostType, CAROUSEL, REEL, SINGLE_IMAGE, Invalid, PostValidation, PostValidator, Reason (+7 more)

### Community 6 - "ComposePostScreen.kt"
Cohesion: 0.10
Nodes (19): mediaModel(), AddMediaCard(), CaptionSection(), ComposePostScreen(), dateFormat(), HashtagSection(), measureImage(), MediaPicker() (+11 more)

### Community 7 - "ScheduleCalculatorTest"
Cohesion: 0.10
Nodes (8): Action, AskUser, MarkMissed, PublishNow, ScheduleCalculator, WaitUntil, PostScheduler, ScheduleCalculatorTest

### Community 8 - "HashtagPresetEntity"
Cohesion: 0.08
Nodes (6): HashtagPresetDao, HashtagPresetEntity, PresetRepository, Factory, PresetDraft, PresetsViewModel

### Community 11 - "HomeScreen.kt"
Cohesion: 0.09
Nodes (12): openExactAlarmSettings(), edgeScrollFor(), EmptyQueue(), HomeScreen(), NoSlotsBanner(), PausedBanner(), postTypeIcon(), postTypeLabel() (+4 more)

### Community 13 - "ScheduleScreen.kt"
Cohesion: 0.12
Nodes (9): shortDayName(), AddSlotDialog(), CatchUpCard(), NoSlots(), PauseCard(), ScheduleScreen(), SectionHeader(), SlotRow() (+1 more)

### Community 14 - "DoneList.kt"
Cohesion: 0.12
Nodes (9): DonePostRow, MediaThumbnail(), Confirmation, DoneCard(), DoneList(), DoneThumbnail(), NothingDoneYet(), outcomeLabel() (+1 more)

### Community 15 - "AuthDtos.kt"
Cohesion: 0.12
Nodes (7): FlexibleIdSerializer, InstagramProfileDto, LongLivedTokenDto, MetaErrorDto, MetaErrorEnvelopeDto, ShortLivedTokenDto, InstagramAuthApi

### Community 17 - "SlotAttempt"
Cohesion: 0.19
Nodes (4): SlotAttempt, SlotLedger, PublishCascadeRegressionTest, SlotLedgerTest

### Community 18 - "ComposePostViewModel"
Cohesion: 0.11
Nodes (7): QueuePreview, TimingMode, FIXED, QUEUED, ComposePostUiState, ComposePostViewModel, PickedMedia

### Community 19 - "CaptionCoachScreen.kt"
Cohesion: 0.15
Nodes (13): ApplyBar(), AskingStage(), CaptionCard(), CaptionCoachScreen(), countChosen(), FailedStage(), Hint(), Label() (+5 more)

### Community 20 - "SettingsScreen.kt"
Cohesion: 0.13
Nodes (8): ConnectedCard(), ConnectingCard(), ConnectionStatusLine(), MessageCard(), NotConnectedCard(), PresetsEntryCard(), ScheduleEntryCard(), SettingsScreen()

### Community 23 - "PostStatus"
Cohesion: 0.11
Nodes (12): NotBeforeHold, SlotAttemptRow, ScheduledPostEntity, FailureKind, PERMANENT, TRANSIENT, PostStatus, CANCELLED (+4 more)

### Community 25 - "QueueFormat.kt"
Cohesion: 0.14
Nodes (4): dayName(), slotLabel(), timeOfDay(), windowLabel()

### Community 26 - "MediaType"
Cohesion: 0.16
Nodes (4): Converters, MediaType, IMAGE, VIDEO

### Community 27 - "DragReorderTest"
Cohesion: 0.21
Nodes (3): DragReorder, ItemBounds, DragReorderTest

### Community 28 - "MainActivity.kt"
Cohesion: 0.14
Nodes (3): AppRoot(), MainActivity, RequestNotificationPermissionOnce()

### Community 30 - "AppEventEntity"
Cohesion: 0.17
Nodes (4): AppEventDao, AppEventEntity, Category, EventLog

### Community 44 - "SettingsViewModel.kt"
Cohesion: 0.15
Nodes (3): Factory, SettingsUiState, SettingsViewModel

### Community 46 - "PublishRepositoryTest"
Cohesion: 0.38
Nodes (3): FakeAccounts, FakeApi, PublishRepositoryTest

### Community 48 - "AccountRepository"
Cohesion: 0.21
Nodes (4): AccountRepository, ConnectResult, Failure, Success

### Community 52 - "PublishDtos.kt"
Cohesion: 0.35
Nodes (7): CloudinaryErrorDto, CloudinaryUploadDto, ContainerStatusDto, PublishedMediaDto, PublishingLimitConfigDto, PublishingLimitDto, PublishingLimitEnvelopeDto

### Community 59 - "QueueRepository.kt"
Cohesion: 0.18
Nodes (5): toSlot(), MissedPostPolicy, ASK_ME, POST_ANYWAY, POST_IF_RECENT

### Community 60 - "CloudinaryUploader"
Cohesion: 0.31
Nodes (3): FakeUploader, CloudinaryUploader, Uploaded

### Community 62 - "Result"
Cohesion: 0.27
Nodes (4): Code, Error, OAuthRedirectBus, Result

### Community 63 - "TokenLifecycle"
Cohesion: 0.20
Nodes (6): Expired, Healthy, ShouldRefresh, State, TokenLifecycle, TooYoungToRefresh

### Community 64 - "render_diagnostics.py"
Cohesion: 0.27
Nodes (3): duration(), main(), when()

### Community 68 - "State"
Cohesion: 0.29
Nodes (7): State, ERROR, EXPIRED, FINISHED, IN_PROGRESS, PUBLISHED, UNKNOWN

### Community 69 - "PresetsScreen"
Cohesion: 0.43
Nodes (4): NoPresetsYet(), PresetCard(), PresetEditor(), PresetsScreen()

## Knowledge Gaps
- **40 isolated node(s):** `IN_PROGRESS`, `FINISHED`, `ERROR`, `EXPIRED`, `PUBLISHED` (+35 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 285 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **44 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AutoInstaApp` connect `AutoInstaApp` to `CaptionCoachTest`, `Test`, `MediaFileStore`, `ComposePostScreen.kt`, `ScheduleCalculatorTest`, `HashtagPresetEntity`, `QueueReorderTest.kt`, `androidTest/java/com/autoinsta/data/media/MediaFileStoreTest.kt`, `HomeScreen.kt`, `PostWorkerTest`, `ScheduleScreen.kt`, `QueueRepository`, `SettingsScreen.kt`, `PublishRepositoryTest.kt`, `AppEventEntity`, `TokenRefreshWorker.kt`, `TokenStore`, `PostWorker.kt`, `PublishRepositoryTest`, `AccountRepository`, `Notifier.kt`, `BootReceiver.kt`, `PostHistoryEntity`, `PostRepository`, `AppDatabase`, `.onCreate`?**
  _High betweenness centrality (0.178) - this node is a cross-community bridge._
- **What connects `IN_PROGRESS`, `FINISHED`, `ERROR` to the rest of the system?**
  _40 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `CaptionCoachTest` be split into smaller, more focused modules?**
  _Cohesion score 0.05265123226288275 - nodes in this community are weakly interconnected._
- **Why does `MediaFit` connect `Test` to `Test`, `CloudinaryUploader.kt`, `MediaFileStore`, `MediaItemEntity`, `ComposePostScreen.kt`, `QueueReorderTest.kt`, `ComposePostViewModel`, `PostRepository`, `MediaType`, `CloudinaryUploader`, `PublishRepositoryTest.kt`, `MediaFitEditor.kt`?**
  _High betweenness centrality (0.063) - this node is a cross-community bridge._
- **Should `Test` be split into smaller, more focused modules?**
  _Cohesion score 0.07108478341355054 - nodes in this community are weakly interconnected._
- **Why does `MediaType` connect `MediaType` to `CaptionCoachTest`, `Test`, `CloudinaryUploader.kt`, `MediaFileStore`, `MediaItemEntity`, `ComposePostScreen.kt`, `QueueReorderTest.kt`, `HomeScreen.kt`, `PostWorkerTest`, `DoneList.kt`, `ComposePostViewModel`, `PostRepository`, `CloudinaryUploader`, `PublishRepositoryTest.kt`?**
  _High betweenness centrality (0.061) - this node is a cross-community bridge._
- **Should `Test` be split into smaller, more focused modules?**
  _Cohesion score 0.07885304659498207 - nodes in this community are weakly interconnected._