package com.longlong.bookmark.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * 项目打开监听器 - 初始化服务
 */
class ProjectOpenListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        // 初始化服务（触发懒加载）
        BookmarkService.getInstance(project)
        TagService.getInstance(project)
        DiagramService.getInstance(project)
        FileChangeListener.getInstance(project)
    }
}
