package com.lm.artifex.mupdfdemo;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import net.youmi.android.AdManager;
import net.youmi.android.banner.AdSize;
import net.youmi.android.banner.AdView;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

enum Purpose {
	PickPDF, PickKeyFile
}

public class ChoosePDFActivity extends Activity implements OnItemClickListener {
	private ListView filelist;
	private FrameLayout adviewContainer;
	static private File mDirectory;
	static private Map<String, Integer> mPositions = new HashMap<String, Integer>();
	private File mParent;
	private File[] mDirs;
	private File[] mFiles;
	private Handler mHandler;
	private Runnable mUpdateFiles;
	private ChoosePDFAdapter adapter;
	private Purpose mPurpose;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		filelist = (ListView) findViewById(R.id.filemanager);
		adviewContainer = (FrameLayout) findViewById(R.id.adview);

		mPurpose = Intent.ACTION_MAIN.equals(getIntent().getAction()) ? Purpose.PickPDF
				: Purpose.PickKeyFile;

		String storageState = Environment.getExternalStorageState();

		if (!Environment.MEDIA_MOUNTED.equals(storageState)
				&& !Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.no_media_warning);
			builder.setMessage(R.string.no_media_hint);
			AlertDialog alert = builder.create();
			alert.setButton(AlertDialog.BUTTON_POSITIVE,
					getString(R.string.dismiss), new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							finish();
						}
					});
			alert.show();
			return;
		}

		if (mDirectory == null)
			mDirectory = Environment.getExternalStorageDirectory();

		// Create a list adapter...
		adapter = new ChoosePDFAdapter(getLayoutInflater());
		filelist.setAdapter(adapter);
		filelist.setOnItemClickListener(this);

		/* 有米广告start */
		// 初始化接口，应用启动的时候调用
		// 参数：appId, appSecret, 调试模式
		AdManager.getInstance(this).init("a21f956d0b31093e",
				"ba177eddaafde91e", false);

		// 实例化LayoutParams(重要)
		FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.WRAP_CONTENT);
		// 设置广告条的悬浮位置
		layoutParams.gravity = Gravity.BOTTOM | Gravity.RIGHT; // 这里示例为右下角
		// 实例化广告条
		AdView adView = new AdView(this, AdSize.FIT_SCREEN);
		// 调用Activity的addContentView函数
		adviewContainer.addView(adView, layoutParams);

		// 监听广告条接口
		/*
		 * adView.setAdListener(new AdViewListener() {
		 * 
		 * @Override public void onSwitchedAd(AdView arg0) {
		 * Log.i("YoumiAdDemo", "广告条切换"); }
		 * 
		 * @Override public void onReceivedAd(AdView arg0) {
		 * Log.i("YoumiAdDemo", "请求广告成功");
		 * 
		 * }
		 * 
		 * @Override public void onFailedToReceivedAd(AdView arg0) {
		 * Log.i("YoumiAdDemo", "请求广告失败"); } });
		 */
		/* 有米广告end */

		// ...that is updated dynamically when files are scanned
		mHandler = new Handler();
		mUpdateFiles = new Runnable() {
			public void run() {
				Resources res = getResources();
				String appName = res.getString(R.string.app_name);
				String version = res.getString(R.string.version);
				String title = res.getString(R.string.picker_title_App_Ver_Dir);
				setTitle(String.format(title, appName, version, mDirectory));

				mParent = mDirectory.getParentFile();

				mDirs = mDirectory.listFiles(new FileFilter() {

					public boolean accept(File file) {
						return file.isDirectory();
					}
				});
				if (mDirs == null)
					mDirs = new File[0];

				mFiles = mDirectory.listFiles(new FileFilter() {

					public boolean accept(File file) {
						if (file.isDirectory())
							return false;
						String fname = file.getName().toLowerCase();
						switch (mPurpose) {
						case PickPDF:
							if (fname.endsWith(".pdf"))
								return true;
							if (fname.endsWith(".xps"))
								return true;
							if (fname.endsWith(".cbz"))
								return true;
							if (fname.endsWith(".png"))
								return true;
							if (fname.endsWith(".jpe"))
								return true;
							if (fname.endsWith(".jpeg"))
								return true;
							if (fname.endsWith(".jpg"))
								return true;
							if (fname.endsWith(".jfif"))
								return true;
							if (fname.endsWith(".jfif-tbnl"))
								return true;
							if (fname.endsWith(".tif"))
								return true;
							if (fname.endsWith(".tiff"))
								return true;
							return false;
						case PickKeyFile:
							if (fname.endsWith(".pfx"))
								return true;
							return false;
						default:
							return false;
						}
					}
				});
				if (mFiles == null)
					mFiles = new File[0];

				Arrays.sort(mFiles, new Comparator<File>() {
					public int compare(File arg0, File arg1) {
						return arg0.getName().compareToIgnoreCase(
								arg1.getName());
					}
				});

				Arrays.sort(mDirs, new Comparator<File>() {
					public int compare(File arg0, File arg1) {
						return arg0.getName().compareToIgnoreCase(
								arg1.getName());
					}
				});

				adapter.clear();
				if (mParent != null)
					adapter.add(new ChoosePDFItem(ChoosePDFItem.Type.PARENT,
							getString(R.string.parent_directory)));
				for (File f : mDirs)
					adapter.add(new ChoosePDFItem(ChoosePDFItem.Type.DIR, f
							.getName()));
				for (File f : mFiles) {
					String fname = f.getName().toLowerCase();
					if (fname.endsWith(".pdf")) {
						adapter.add(new ChoosePDFItem(ChoosePDFItem.Type.PDF, f
								.getName()));
					}
					if (fname.endsWith(".xps") || fname.endsWith(".cbz")
							|| fname.endsWith(".jfif")
							|| fname.endsWith(".jfif-tbnl")
							|| fname.endsWith(".tif")
							|| fname.endsWith(".tiff")
							|| fname.endsWith(".pfx")) {
						adapter.add(new ChoosePDFItem(
								ChoosePDFItem.Type.DOCOTHER, f.getName()));
					}
					if (fname.endsWith(".png") || fname.endsWith(".jpe")
							|| fname.endsWith(".jpeg")
							|| fname.endsWith(".jpg")) {
						adapter.add(new ChoosePDFItem(ChoosePDFItem.Type.IMG, f
								.getName()));
					}
				}

				lastPosition();
			}
		};

		// Start initial file scan...
		mHandler.post(mUpdateFiles);

		// ...and observe the directory and scan files upon changes.
		FileObserver observer = new FileObserver(mDirectory.getPath(),
				FileObserver.CREATE | FileObserver.DELETE) {
			public void onEvent(int event, String path) {
				mHandler.post(mUpdateFiles);
			}
		};
		observer.startWatching();
	}

	private void lastPosition() {
		String p = mDirectory.getAbsolutePath();
		if (mPositions.containsKey(p))
			filelist.setSelection(mPositions.get(p));
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		mPositions.put(mDirectory.getAbsolutePath(),
				filelist.getFirstVisiblePosition());

		if (position < (mParent == null ? 0 : 1)) {
			mDirectory = mParent;
			mHandler.post(mUpdateFiles);
			return;
		}

		position -= (mParent == null ? 0 : 1);

		if (position < mDirs.length) {
			mDirectory = mDirs[position];
			mHandler.post(mUpdateFiles);
			return;
		}

		position -= mDirs.length;

		Uri uri = Uri.parse(mFiles[position].getAbsolutePath());
		Intent intent = new Intent(this, MuPDFActivity.class);
		intent.setAction(Intent.ACTION_VIEW);
		intent.setData(uri);
		switch (mPurpose) {
		case PickPDF:
			// Start an activity to display the PDF file
			intent.putExtra("firstView", true);
			startActivity(intent);
			break;
		case PickKeyFile:
			// Return the uri to the caller
			setResult(RESULT_OK, intent);
			finish();
			break;
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		mPositions.put(mDirectory.getAbsolutePath(),
				filelist.getFirstVisiblePosition());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_about) {
			AlertDialog.Builder dialog = new AlertDialog.Builder(
					ChoosePDFActivity.this);
			dialog.setTitle(R.string.action_about);
			TextView v = new TextView(ChoosePDFActivity.this);
			v.setText(getString(R.string.about_res));
			v.setPadding(10, 10, 10, 10);
			dialog.setView(v);
			dialog.setPositiveButton(R.string.okay, new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			dialog.create().show();
		}
		return super.onOptionsItemSelected(item);
	}

}
