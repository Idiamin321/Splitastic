<template>
  <div class="formData">
    <b-loading v-if="loading" :is-full-page="true" :active="loading"></b-loading>
    <template v-if="atLeastOneRefreshDone">
      <b-upload v-model="newProfileImage" accept="image/*">
        <img class="groupImage" :src="group.image.url" alt="Image" />
      </b-upload>
      <br />
      <p>Tap image to change</p>
      <span v-if="newProfileImage" class="tag is-primary">
        <button class="removeNewFile delete is-small" type="button" @click="removeNewFile()"></button>
        Change to: {{ newProfileImage.name }}
      </span>

      <b-field label="Name">
        <b-input v-model="group.name" :readonly="readOnly"></b-input>
      </b-field>
      <b-field id="descField" label="Description">
        <b-input v-model="group.description" maxlength="255" type="textarea" :readonly="readOnly"></b-input>
      </b-field>

      <section id="saveButton" v-if="!readOnly">
        <b-button v-if="!savingGroupDetails" @click="saveGroupDetails">SAVE</b-button>
        <b-icon v-else icon="sync" type="is-success" class="fa-spin"></b-icon>
      </section>

      <div class="panel">
        <p class="panel-heading">Users</p>
        <div
          class="margin0 panel-block level is-mobile"
          v-for="user in this.group.users"
          :key="user.id"
        >
          <div class="userNameContainer level-left level-item">
            <img class="userImage" :src="user.image.url" alt="Image" />
            <p class="userName">{{ user.name }}</p>
          </div>

          <div class="level-right">
            <b-icon
              v-if="isOwner(user.id)"
              class="level-item"
              icon="crown"
              size="is-small"
              type="is-warning"
            ></b-icon>

            <a v-if="!readOnly && user.isVirtual" class="level-item" @click="editVirtualUser(user)">
              <b-icon icon="edit" size="is-small"></b-icon>
            </a>
            <a
              v-if="!isMyUserId(user.id) && !readOnly"
              class="level-item"
              @click="deleteUserWithConfirm(user.id)"
            >
              <b-icon icon="trash" size="is-small" type="is-danger"></b-icon>
            </a>
          </div>
        </div>
      </div>

      <div v-if="!readOnly" class="panel">
        <div class="margin0 panel-heading level is-mobile">
          <p class="level-left level-item">Open Invites</p>

          <a class="level-right level-item" @click="isInviteModalActive = true">
            <b-icon icon="plus-circle" size="is-small" type="is-success"></b-icon>
          </a>
        </div>

        <div
          class="margin0 panel-block level is-mobile"
          v-for="invite in this.invites"
          :key="invite.id"
        >
          <p class="level-left level-item">{{ invite.email }}</p>
          <a class="level-right level-item" @click="deleteInviteWithConfirm(invite.id)">
            <b-icon icon="trash" size="is-small" type="is-danger"></b-icon>
          </a>
        </div>
      </div>

      <div class="panel">
          <b-button
            class="export-button"
            type="is-warning"
            icon-left="download"
            @click="exportFinanceWithConfirm()"
          >Export Finance</b-button>
          <b-button
            class="export-button"
            type="is-warning"
            icon-left="download"
            @click="exportChoresWithConfirm()"
          >Export Chores</b-button>
      </div>

      <div v-if="!readOnly" class="addImaginaryFriendButtonContainer">
        <b-button
          type="is-success"
          icon-left="plus"
          @click="addImaginaryFriend"
        >Add Imaginary Friend</b-button>
      </div>
      <div v-if="!readOnly" class="addImaginaryFriendButtonContainer">
        <MoveEntriesButton :group="group"></MoveEntriesButton>
      </div>
      <b-button
        v-if="isMyUserId(group.owner) && !readOnly"
        type="is-danger"
        icon-left="trash"
        @click="deleteGroupWithConfirm(group.id)"
      >Delete Group</b-button>
      <b-button
        v-if="!isMyUserId(group.owner) && readOnly"
        type="is-danger"
        icon-left="trash"
        @click="leaveGroupWithConfirm(group.id)"
      >Leave Group</b-button>

      <b-modal :active.sync="isInviteModalActive" scroll="keep">
        <section class="section">
          <div class="card">
            <div class="card-content">
              <b-field class="email" label="Email">
                <b-input type="email" icon="envelope" placeholder="Email" v-model="inviteEmail"></b-input>
              </b-field>
              <b-button @click="sendInvite">SEND INVITE</b-button>
            </div>
          </div>
        </section>
      </b-modal>

      <b-modal :active.sync="isAddImaginaryFriendModalActive" scroll="keep">
        <AddOrEditImaginaryFriend
          :groupId="group.id"
          :user="null"
          @user-created="imaginaryFriendCreated"
        />
      </b-modal>
      <b-modal :active.sync="isEditImaginaryFriendModalActive" scroll="keep">
        <AddOrEditImaginaryFriend
          :groupId="group.id"
          :user="virtualUserToEdit"
          @user-created="imaginaryFriendCreated"
        />
      </b-modal>
    </template>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Prop } from "vue-property-decorator";
import { Group, GroupApi, Invite, UserApi, User, ChoreApi, FinanceApi } from "@/generated/api-axios";
import config from "@/../config";
import { RouterNames } from "@/untils/RouterNames";
import { FileUtils } from "@/untils/FileUtils";
import { StateUtils } from "@/untils/StateUtils";
import AddOrEditImaginaryFriend from "@/components/AddOrEditImaginaryFriend.vue";
import MoveEntriesButton from "@/components/EditGroup/MoveEntries/MoveEntriesButton.vue";

@Component({
  components: {
    AddOrEditImaginaryFriend,
    MoveEntriesButton
  }
})
export default class ShowEditGroup extends Vue {
  @Prop()
  private readOnly!: boolean;
  private atLeastOneRefreshDone = false;
  private group!: Group;
  private invites!: Invite[];
  private loading = true;
  private inviteEmail = "";
  private newProfileImage: File | null = null;

  private savingGroupDetails = false;
  private isInviteModalActive = false;
  private isAddImaginaryFriendModalActive = false;
  private isEditImaginaryFriendModalActive = false;

  private virtualUserToEdit: User | null = null;

  private editVirtualUser(user: User) {
    this.virtualUserToEdit = user;
    this.isEditImaginaryFriendModalActive = true;
  }

  private imaginaryFriendCreated() {
    this.isAddImaginaryFriendModalActive = false;
    this.loading = true;
    this.$router.go(0);
  }

  private removeNewFile() {
    this.newProfileImage = null;
  }

  private isMyUserId(userId: number) {
    return localStorage.userId == userId;
  }

  private isOwner(userId: number) {
    return this.group.owner == userId;
  }

  private async addImaginaryFriend() {
    this.isAddImaginaryFriendModalActive = true;
  }

  private async exportChoresWithConfirm() {
    this.$buefy.dialog.confirm({
      title: "Export Chores",
      message: "Are you sure you want to export all chores?",
      confirmText: "Export",
      type: "is-warning",
      hasIcon: true,
      onConfirm: () => this.exportChores()
    });
  }

  private async exportChores() {
    const api = new ChoreApi({
      basePath: config.basePath,
      apiKey: StateUtils.getApiKey()
    });

    try {
      this.loading = true;
      const apiResult = (await api.choreExportGroupGroupIdGet(Number(this.group.id))).data;
      const name = "export-chores-"+(new Date().toISOString())+".csv";
      this.downloadToFile(name, apiResult);
    } catch (e) {
      this.$toast.error(`Export failed`);
    }
    this.loading = false;
  }

  private downloadToFile(fileaName: string, content:string) {
      const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = fileaName;
      link.click();
      URL.revokeObjectURL(link.href);
  }
  
  private async exportFinance() {
    const api = new FinanceApi({
      basePath: config.basePath,
      apiKey: StateUtils.getApiKey()
    });

    try {
      this.loading = true;
      const apiResult = (await api.financeExportGroupGroupIdGet(Number(this.group.id))).data;
      const name = "export-finance-"+(new Date().toISOString())+".csv";
      this.downloadToFile(name, apiResult);
    } catch (e) {
      this.$toast.error(`Export failed`);
    }
    this.loading = false;
  }

  private async exportFinanceWithConfirm() {
    this.$buefy.dialog.confirm({
      title: "Export finance",
      message: "Are you sure you want to export all finance?",
      confirmText: "Export",
      type: "is-warning",
      hasIcon: true,
      onConfirm: () => this.exportFinance()
    });
  }

  private async leaveGroupWithConfirm() {
    this.$buefy.dialog.confirm({
      title: "Leave Group",
      message:
        "Are you sure you want to <b>leave</b> this group? This action cannot be undone.",
      confirmText: "Leave Group",
      type: "is-danger",
      hasIcon: true,
      onConfirm: () => this.deleteUser(localStorage.userId, true)
    });
  }

  private async deleteGroupWithConfirm(groupId: number) {
    this.$buefy.dialog.confirm({
      title: "Deleting Group",
      message:
        "Are you sure you want to <b>delete</b> this group? This action cannot be undone.",
      confirmText: "Delete Group",
      type: "is-danger",
      hasIcon: true,
      onConfirm: () => this.deleteGroup(groupId)
    });
  }
  private async deleteGroup(groupId: number) {
    const groupApi = new GroupApi({
      basePath: config.basePath,
      apiKey: StateUtils.getApiKey()
    });
    try {
      this.loading = true;
      await groupApi.groupGroupIdDelete(groupId);
      StateUtils.unsetActiveGroupId();
      this.$router.push({ name: RouterNames.HOME });
    } catch (e) {
      this.$toast.error(`Delete failed`);
    }
    this.loading = false;
  }

  private async deleteUserWithConfirm(userId: number) {
    this.$buefy.dialog.confirm({
      title: "Remove User",
      message:
        "Are you sure you want to <b>remove</b> this user? This action cannot be undone.",
      confirmText: "Remove User",
      type: "is-danger",
      hasIcon: true,
      onConfirm: () => this.deleteUser(userId, false)
    });
  }

  private async deleteUser(userId: number, isLeave: boolean) {
    const api = new UserApi({
      basePath: config.basePath,
      apiKey: StateUtils.getApiKey()
    });
    try {
      const countBeforeUserDelete = this.group.users!.length;
      this.loading = true;
      await api.groupGroupIdUserUserIdDelete(Number(this.group.id), userId);
      if (isLeave) {
        StateUtils.unsetActiveGroupId();
        this.$router.push({ name: RouterNames.HOME });
      } else {
        await this.refresh();
        const countAfterUserDelete = this.group.users!.length;
        if (countBeforeUserDelete == countAfterUserDelete) {
          this.$toast.error(
            `A ghost of this user is still here because he has entries in the group.`,
            {
              timeout: 4000
            }
          );
        } else {
          this.$toast.success(`User deleted`);
        }
      }
    } catch (e) {
      this.loading = false;
      this.$toast.error(`Delete failed`);
    }
  }

  private async deleteInviteWithConfirm(inviteId: number) {
    this.$buefy.dialog.confirm({
      title: "Delete Invite",
      message:
        "Are you sure you want to <b>delete</b> this invite? This action cannot be undone.",
      confirmText: "Delete Invite",
      type: "is-danger",
      hasIcon: true,
      onConfirm: () => this.deleteInvite(inviteId)
    });
  }

  private async deleteInvite(inviteId: number) {
    const groupApi = new GroupApi({
      basePath: config.basePath,
      apiKey: StateUtils.getApiKey()
    });
    try {
      this.loading = true;
      await groupApi.groupInviteInviteIdDelete(inviteId);
      this.refresh();
    } catch (e) {
      this.loading = false;
      this.$toast.error(`Delete failed`);
    }
  }
  private async sendInvite() {
    const api = new UserApi({
      basePath: config.basePath,
      apiKey: StateUtils.getApiKey()
    });

    try {
      this.loading = true;
      await api.groupGroupIdUserEmailPut(
        Number(this.group.id),
        this.inviteEmail
      );
      this.refresh();
    } catch (e) {
      this.loading = false;
      this.$toast.error(`Invite failed`);
    }
  }

  private async saveGroupDetails() {
    this.savingGroupDetails = true;
    const groupApi = new GroupApi({
      basePath: config.basePath,
      apiKey: StateUtils.getApiKey()
    });

    if (this.newProfileImage) {
      this.group!.image = {
        url: await FileUtils.loadToDataURL(this.newProfileImage)
      };
    } else {
      this.group!.image = undefined;
    }

    await groupApi.groupPut(this.group);
    this.$toast.success(`Successfully updated`);

    this.refresh();

    this.savingGroupDetails = false;
  }

  private async loadInvites(groupApi: GroupApi) {
    if (localStorage.userId == this.group.owner) {
      this.invites = (
        await groupApi.groupInviteGroupIdGet(Number(this.$route.params.groupId))
      ).data;
    } else {
      this.invites = [];
    }
  }

  private async refresh() {
    this.newProfileImage = null;
    this.isInviteModalActive = false;
    this.loading = true;
    const groupApi = new GroupApi({
      basePath: config.basePath,
      apiKey: StateUtils.getApiKey()
    });

    this.group = (
      await groupApi.groupGroupIdGet(Number(this.$route.params.groupId))
    ).data;

    await this.loadInvites(groupApi);
    this.loading = false;
    this.atLeastOneRefreshDone = true;
  }

  private async mounted() {
    return this.refresh();
  }
}
</script>

<style scoped lang="scss">
#saveButton {
  margin-bottom: 1em;
}

.addImaginaryFriendButtonContainer {
  margin-bottom: 1em;
}
#descField {
  margin-bottom: 0;
}

.margin0 {
  margin: 0;
}

.userName {
  margin-left: 0.75em;
  display: inline;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.userNameContainer {
  max-width: 90%;
}

.userImage {
  border-radius: 50%;
  object-fit: cover;
  width: 3em;
  height: 3em;
}

.groupImage {
  border-radius: 50%;
  object-fit: cover;
  height: 6em;
  width: 6em;
}

.formData {
  background-color: $transbarentBackground;
  padding: $formDataPadding;
  margin: 1em;
}

.export-button{
  margin: 0.5em;
}
</style>
